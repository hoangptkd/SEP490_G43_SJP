import type { AiInterviewLiveTranscriptionSession } from '../types/aiInterview';
import type {
  AnswerTranscriptionCallbacks,
  AnswerTranscriptionProvider,
  AnswerTranscriptionResult,
} from './answerTranscriptionProvider';

const CONNECT_TIMEOUT_MS = 5_000;
const FINAL_FLUSH_TIMEOUT_MS = 5_000;
const WORKLET_NAME = 'gladia-pcm-capture';
const GLADIA_SAMPLE_RATE = 16_000;

interface TranscriptMessage {
  type?: string;
  data?: {
    id?: string;
    is_final?: boolean;
    utterance?: { text?: string; start?: number };
  };
}

interface TranscriptPiece {
  key: string;
  text: string;
  order: number;
  start?: number;
}

export class GladiaLiveAnswerTranscriptionProvider implements AnswerTranscriptionProvider {
  readonly kind = 'gladia_live' as const;

  private audioContext?: AudioContext;
  private sourceNode?: MediaStreamAudioSourceNode;
  private workletNode?: AudioWorkletNode;
  private silentGain?: GainNode;
  private websocket?: WebSocket;
  private liveSession?: AiInterviewLiveTranscriptionSession;
  private pieces = new Map<string, TranscriptPiece>();
  private interimTranscript = '';
  private sequence = 0;
  private sending = false;
  private finishing = false;
  private disposed = false;
  private reconnectAttempted = false;
  private finishResolver?: () => void;
  private connectTimer?: number;
  private flushTimer?: number;

  constructor(
    private readonly callbacks: AnswerTranscriptionCallbacks,
    private readonly createLiveSession: (sampleRate: number) => Promise<AiInterviewLiveTranscriptionSession>,
  ) {}

  async start(stream: MediaStream, initialTranscript: string) {
    if (this.disposed) throw new Error('Gladia Live provider đã bị đóng');
    if (!('AudioWorkletNode' in window)) throw new Error('Trình duyệt không hỗ trợ AudioWorklet');
    this.appendExternalTranscript(initialTranscript);
    const AudioContextClass = window.AudioContext;
    if (!AudioContextClass) throw new Error('Trình duyệt không hỗ trợ Web Audio API');
    const context = new AudioContextClass({ latencyHint: 'interactive', sampleRate: GLADIA_SAMPLE_RATE });
    this.audioContext = context;
    await context.audioWorklet.addModule('/gladia-pcm-worklet.js');
    this.liveSession = await this.createLiveSession(context.sampleRate);
    await this.connect(this.liveSession.websocketUrl);

    this.sourceNode = context.createMediaStreamSource(stream);
    this.workletNode = new AudioWorkletNode(context, WORKLET_NAME, {
      numberOfInputs: 1,
      numberOfOutputs: 1,
      outputChannelCount: [1],
      channelCount: 1,
    });
    this.silentGain = context.createGain();
    this.silentGain.gain.value = 0;
    this.workletNode.port.onmessage = (event: MessageEvent<ArrayBuffer>) => {
      if (!this.sending || this.websocket?.readyState !== WebSocket.OPEN) return;
      this.websocket.send(event.data);
    };
    this.sourceNode.connect(this.workletNode);
    this.workletNode.connect(this.silentGain);
    this.silentGain.connect(context.destination);
    await context.resume();
    this.sending = true;
  }

  pause() {
    this.sending = false;
  }

  async resume() {
    if (this.disposed || this.finishing) throw new Error('Phiên Gladia Live không thể tiếp tục');
    if (this.audioContext?.state === 'suspended') await this.audioContext.resume();
    this.sending = true;
  }

  snapshotTranscript() {
    const transcript = this.committedTranscript();
    this.interimTranscript = '';
    this.emit();
    return transcript;
  }

  appendExternalTranscript(text: string) {
    const normalized = normalize(text);
    if (!normalized) return;
    const key = `external-${this.sequence}`;
    this.pieces.set(key, { key, text: normalized, order: this.sequence });
    this.sequence += 1;
    this.emit();
  }

  async finish(): Promise<AnswerTranscriptionResult> {
    this.sending = false;
    this.finishing = true;
    if (this.websocket?.readyState === WebSocket.OPEN) {
      this.websocket.send(JSON.stringify({ type: 'stop_recording' }));
      await new Promise<void>((resolve) => {
        this.finishResolver = resolve;
        this.flushTimer = window.setTimeout(resolve, FINAL_FLUSH_TIMEOUT_MS);
      });
    }
    this.interimTranscript = '';
    this.emit();
    const result: AnswerTranscriptionResult = {
      source: this.kind,
      transcript: this.committedTranscript(),
      liveSessionToken: this.liveSession?.sessionToken,
    };
    this.disposeAudioGraph();
    this.closeSocket();
    return result;
  }

  abort() {
    this.sending = false;
    this.finishing = true;
    this.disposeAudioGraph();
    this.closeSocket();
  }

  dispose() {
    if (this.disposed) return;
    this.disposed = true;
    this.abort();
  }

  private connect(url: string) {
    return new Promise<void>((resolve, reject) => {
      const socket = new WebSocket(url);
      socket.binaryType = 'arraybuffer';
      this.websocket = socket;
      let opened = false;
      this.connectTimer = window.setTimeout(() => {
        if (opened) return;
        socket.close();
        reject(new Error('Gladia Live kết nối quá lâu'));
      }, CONNECT_TIMEOUT_MS);
      socket.onopen = () => {
        opened = true;
        window.clearTimeout(this.connectTimer);
        resolve();
      };
      socket.onmessage = (event) => this.handleMessage(event.data);
      socket.onerror = () => {
        if (!opened) {
          window.clearTimeout(this.connectTimer);
          reject(new Error('Không thể kết nối Gladia Live'));
        }
      };
      socket.onclose = () => {
        window.clearTimeout(this.connectTimer);
        if (this.finishing) {
          this.resolveFinish();
          return;
        }
        if (!opened) return;
        void this.reconnect();
      };
    });
  }

  private async reconnect() {
    if (this.reconnectAttempted || this.disposed || this.finishing || !this.liveSession) {
      this.callbacks.onTerminalError('Gladia Live đã mất kết nối. Transcript đã nhận vẫn được giữ lại.');
      return;
    }
    this.reconnectAttempted = true;
    this.sending = false;
    try {
      await this.connect(this.liveSession.websocketUrl);
      this.sending = true;
    } catch {
      this.callbacks.onTerminalError('Không thể kết nối lại Gladia Live. Transcript đã nhận vẫn được giữ lại.');
    }
  }

  private handleMessage(raw: unknown) {
    if (typeof raw !== 'string') return;
    let message: TranscriptMessage;
    try {
      message = JSON.parse(raw) as TranscriptMessage;
    } catch {
      return;
    }
    if (message.type === 'end_session' || message.type === 'end_recording') {
      this.resolveFinish();
      return;
    }
    if (message.type === 'error') {
      this.callbacks.onTerminalError('Gladia Live báo lỗi trong lúc nhận dạng.');
      return;
    }
    if (message.type !== 'transcript') return;
    const id = message.data?.id;
    const text = normalize(message.data?.utterance?.text || '');
    if (!id || !text) return;
    if (!message.data?.is_final) {
      this.interimTranscript = text;
      this.emit();
      return;
    }
    const existing = this.pieces.get(id);
    this.pieces.set(id, {
      key: id,
      text,
      order: existing?.order ?? this.sequence++,
      start: message.data.utterance?.start,
    });
    this.interimTranscript = '';
    this.emit();
  }

  private emit() {
    this.callbacks.onUpdate({
      committedTranscript: this.committedTranscript(),
      interimTranscript: this.interimTranscript,
    });
  }

  private committedTranscript() {
    return [...this.pieces.values()]
      .sort((left, right) => {
        if (left.start != null && right.start != null && left.start !== right.start) return left.start - right.start;
        return left.order - right.order;
      })
      .map((piece) => piece.text)
      .join(' ')
      .replace(/\s+/g, ' ')
      .trim();
  }

  private resolveFinish() {
    window.clearTimeout(this.flushTimer);
    const resolve = this.finishResolver;
    this.finishResolver = undefined;
    resolve?.();
  }

  private closeSocket() {
    window.clearTimeout(this.connectTimer);
    window.clearTimeout(this.flushTimer);
    const socket = this.websocket;
    this.websocket = undefined;
    if (!socket) return;
    socket.onopen = null;
    socket.onmessage = null;
    socket.onerror = null;
    socket.onclose = null;
    if (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING) {
      socket.close(1000);
    }
    this.resolveFinish();
  }

  private disposeAudioGraph() {
    if (this.workletNode) this.workletNode.port.onmessage = null;
    try { this.sourceNode?.disconnect(); } catch { /* already disconnected */ }
    try { this.workletNode?.disconnect(); } catch { /* already disconnected */ }
    try { this.silentGain?.disconnect(); } catch { /* already disconnected */ }
    this.sourceNode = undefined;
    this.workletNode = undefined;
    this.silentGain = undefined;
    const context = this.audioContext;
    this.audioContext = undefined;
    if (context && context.state !== 'closed') void context.close();
  }
}

function normalize(value: string) {
  return value.replace(/\s+/g, ' ').trim();
}

import type { AiInterviewTranscriptionTicket } from '../types/aiInterview';
import type {
  AnswerTranscriptionCallbacks,
  AnswerTranscriptionProvider,
  AnswerTranscriptionResult,
} from './answerTranscriptionProvider';

const CONNECT_TIMEOUT_MS = 10_000;
const DEFAULT_FINAL_FLUSH_TIMEOUT_MS = 6_000;
const MAX_BUFFERED_AUDIO_BYTES = 256 * 1024;
const MAX_PENDING_AUDIO_BYTES = 160 * 1024;
const WORKLET_NAME = 'speechmatics-pcm-capture';

interface SpeechmaticsMessage {
  message?: string;
  type?: string;
  reason?: string;
  metadata?: {
    transcript?: string;
    start_time?: number;
    end_time?: number;
  };
}

interface TranscriptPiece {
  key: string;
  text: string;
  order: number;
  start?: number;
}

export class SpeechmaticsRealtimeAnswerTranscriptionProvider implements AnswerTranscriptionProvider {
  readonly kind = 'speechmatics_realtime' as const;

  private audioContext?: AudioContext;
  private sourceNode?: MediaStreamAudioSourceNode;
  private workletNode?: AudioWorkletNode;
  private silentGain?: GainNode;
  private websocket?: WebSocket;
  private pieces = new Map<string, TranscriptPiece>();
  private interimTranscript = '';
  private sequence = 0;
  private sending = false;
  private finishing = false;
  private disposed = false;
  private recognitionStarted = false;
  private connectResolver?: () => void;
  private connectRejecter?: (error: Error) => void;
  private finishResolver?: () => void;
  private connectTimer?: number;
  private flushTimer?: number;
  private pendingAudio: ArrayBuffer[] = [];
  private pendingAudioBytes = 0;
  private finalFlushTimeoutMs = DEFAULT_FINAL_FLUSH_TIMEOUT_MS;

  constructor(
    private readonly callbacks: AnswerTranscriptionCallbacks,
    private readonly createTicket: () => Promise<AiInterviewTranscriptionTicket>,
  ) {}

  async start(stream: MediaStream, initialTranscript: string) {
    if (this.disposed) throw new Error('Speechmatics Realtime provider đã bị đóng');
    if (!('AudioWorkletNode' in window)) throw new Error('Trình duyệt không hỗ trợ AudioWorklet');
    const AudioContextClass = window.AudioContext;
    if (!AudioContextClass) throw new Error('Trình duyệt không hỗ trợ Web Audio API');
    this.appendExternalTranscript(initialTranscript);
    const ticketPromise = this.createTicket();
    const context = new AudioContextClass({ latencyHint: 'interactive' });
    this.audioContext = context;
    await context.audioWorklet.addModule('/speechmatics-pcm-worklet.js');
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
      const socket = this.websocket;
      if (!this.sending) return;
      if (!this.recognitionStarted || socket?.readyState !== WebSocket.OPEN) {
        if (this.pendingAudioBytes + event.data.byteLength <= MAX_PENDING_AUDIO_BYTES) {
          this.pendingAudio.push(event.data);
          this.pendingAudioBytes += event.data.byteLength;
        }
        return;
      }
      if (socket.bufferedAmount > MAX_BUFFERED_AUDIO_BYTES) return;
      socket.send(event.data);
    };
    this.sourceNode.connect(this.workletNode);
    this.workletNode.connect(this.silentGain);
    this.silentGain.connect(context.destination);
    await context.resume();
    this.sending = true;
    const ticket = await ticketPromise;
    this.finalFlushTimeoutMs = Math.max(1_000, ticket.finalFlushTimeoutMs || DEFAULT_FINAL_FLUSH_TIMEOUT_MS);
    await this.connect(resolveWebSocketUrl(ticket.websocketPath));
  }

  pause() {
    this.sending = false;
  }

  async resume() {
    if (this.disposed || this.finishing) throw new Error('Phiên Speechmatics không thể tiếp tục');
    if (this.audioContext?.state === 'suspended') await this.audioContext.resume();
    this.sending = true;
  }

  snapshotTranscript() {
    const transcript = normalize(`${this.committedTranscript()} ${this.interimTranscript}`);
    this.interimTranscript = '';
    return transcript;
  }

  appendExternalTranscript(text: string) {
    const normalized = normalize(text);
    if (!normalized) return;
    const key = `external-${this.sequence}`;
    this.pieces.set(key, { key, text: normalized, order: this.sequence++ });
    this.emit();
  }

  async finish(): Promise<AnswerTranscriptionResult> {
    this.sending = false;
    this.finishing = true;
    if (this.websocket?.readyState === WebSocket.OPEN && this.recognitionStarted) {
      this.websocket.send(JSON.stringify({ message: 'EndOfStream' }));
      await new Promise<void>((resolve) => {
        this.finishResolver = resolve;
        this.flushTimer = window.setTimeout(resolve, this.finalFlushTimeoutMs);
      });
    }
    this.interimTranscript = '';
    this.emit();
    const result: AnswerTranscriptionResult = {
      source: this.kind,
      transcript: this.committedTranscript(),
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
      this.connectResolver = resolve;
      this.connectRejecter = reject;
      this.connectTimer = window.setTimeout(() => {
        this.rejectConnect(new Error('Speechmatics Realtime kết nối quá lâu'));
        socket.close();
      }, CONNECT_TIMEOUT_MS);
      socket.onmessage = (event) => this.handleMessage(event.data);
      socket.onerror = () => this.rejectConnect(new Error('Không thể kết nối Speechmatics Realtime'));
      socket.onclose = () => {
        window.clearTimeout(this.connectTimer);
        if (!this.recognitionStarted) {
          this.rejectConnect(new Error('Speechmatics Realtime không khởi động được'));
        } else if (!this.finishing && !this.disposed) {
          this.callbacks.onTerminalError('Speechmatics Realtime đã mất kết nối. Transcript đã nhận vẫn được giữ lại.');
        }
        this.resolveFinish();
      };
    });
  }

  private handleMessage(raw: unknown) {
    if (typeof raw !== 'string') return;
    let message: SpeechmaticsMessage;
    try {
      message = JSON.parse(raw) as SpeechmaticsMessage;
    } catch {
      return;
    }
    if (message.message === 'RecognitionStarted') {
      this.recognitionStarted = true;
      const socket = this.websocket;
      if (socket?.readyState === WebSocket.OPEN) {
        this.pendingAudio.forEach((chunk) => socket.send(chunk));
      }
      this.pendingAudio = [];
      this.pendingAudioBytes = 0;
      window.clearTimeout(this.connectTimer);
      const resolve = this.connectResolver;
      this.connectResolver = undefined;
      this.connectRejecter = undefined;
      resolve?.();
      return;
    }
    if (message.message === 'EndOfTranscript') {
      this.resolveFinish();
      return;
    }
    if (message.message === 'Error' || message.message === 'ProxyError') {
      const reason = message.type === 'quota_exceeded'
        ? 'Speechmatics đã đạt giới hạn phiên đồng thời.'
        : 'Speechmatics không thể tiếp tục nhận dạng giọng nói.';
      if (!this.recognitionStarted) this.rejectConnect(new Error(reason));
      else this.callbacks.onTerminalError(reason);
      this.resolveFinish();
      return;
    }
    const text = normalize(message.metadata?.transcript || '');
    if (!text) return;
    if (message.message === 'AddPartialTranscript') {
      this.interimTranscript = text;
      this.emit();
      return;
    }
    if (message.message !== 'AddTranscript') return;
    const start = message.metadata?.start_time;
    const end = message.metadata?.end_time;
    const key = start == null ? `final-${this.sequence}` : `final-${start}-${end ?? ''}`;
    const existing = this.pieces.get(key);
    this.pieces.set(key, {
      key,
      text,
      order: existing?.order ?? this.sequence++,
      start,
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

  private rejectConnect(error: Error) {
    window.clearTimeout(this.connectTimer);
    const reject = this.connectRejecter;
    this.connectResolver = undefined;
    this.connectRejecter = undefined;
    reject?.(error);
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
    if (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING) socket.close(1000);
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
    this.pendingAudio = [];
    this.pendingAudioBytes = 0;
    const context = this.audioContext;
    this.audioContext = undefined;
    if (context && context.state !== 'closed') void context.close();
  }
}

export function resolveWebSocketUrl(path: string) {
  if (/^wss?:\/\//i.test(path)) return path;
  const apiBase = import.meta.env.VITE_API_URL || '/api';
  if (/^https?:\/\//i.test(apiBase)) {
    return `${apiBase.replace(/^http/i, 'ws').replace(/\/$/, '')}${path.startsWith('/') ? path : `/${path}`}`;
  }
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const normalizedBase = apiBase.startsWith('/') ? apiBase : `/${apiBase}`;
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  return `${protocol}//${window.location.host}${normalizedBase.replace(/\/$/, '')}${normalizedPath}`;
}

function normalize(value: string) {
  return value.replace(/\s+/g, ' ').trim();
}

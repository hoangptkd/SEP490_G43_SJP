import type {
  AnswerTranscriptionCallbacks,
  AnswerTranscriptionProvider,
  AnswerTranscriptionResult,
} from './answerTranscriptionProvider';

const VIETNAMESE_LANG = 'vi-VN';

export class WebSpeechAnswerTranscriptionProvider implements AnswerTranscriptionProvider {
  readonly kind = 'web_speech' as const;

  private recognition: SpeechRecognitionLike | null = null;
  private committedTranscript = '';
  private interimTranscript = '';
  private active = false;
  private paused = false;
  private disposed = false;
  private restartTimer?: number;

  constructor(
    private readonly callbacks: AnswerTranscriptionCallbacks,
    private readonly restartDelayMs: number,
  ) {}

  async start(_stream: MediaStream, initialTranscript: string) {
    this.committedTranscript = normalize(initialTranscript);
    this.interimTranscript = '';
    this.active = true;
    this.paused = false;
    this.disposed = false;
    this.startRecognition();
  }

  pause() {
    this.paused = true;
    window.clearTimeout(this.restartTimer);
    this.abortRecognition();
  }

  async resume() {
    if (this.disposed) throw new Error('Web Speech provider đã bị đóng');
    this.active = true;
    this.paused = false;
    this.startRecognition();
  }

  snapshotTranscript() {
    this.committedTranscript = append(this.committedTranscript, this.interimTranscript);
    this.interimTranscript = '';
    this.emit();
    return this.committedTranscript;
  }

  appendExternalTranscript(text: string) {
    this.committedTranscript = append(this.committedTranscript, text);
    this.interimTranscript = '';
    this.emit();
  }

  async finish(): Promise<AnswerTranscriptionResult> {
    this.committedTranscript = append(this.committedTranscript, this.interimTranscript);
    this.active = false;
    this.paused = true;
    window.clearTimeout(this.restartTimer);
    this.abortRecognition();
    this.interimTranscript = '';
    this.emit();
    return { source: this.kind, transcript: this.committedTranscript };
  }

  abort() {
    this.active = false;
    this.paused = true;
    window.clearTimeout(this.restartTimer);
    this.abortRecognition();
  }

  dispose() {
    this.disposed = true;
    this.abort();
  }

  private startRecognition() {
    if (!this.active || this.paused || this.disposed || this.recognition) return;
    const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!Recognition) throw new Error('Trình duyệt không hỗ trợ Web Speech API');
    const recognition = new Recognition();
    recognition.continuous = true;
    recognition.interimResults = true;
    recognition.lang = VIETNAMESE_LANG;
    recognition.maxAlternatives = 1;
    recognition.onresult = (event) => {
      let interim = '';
      let finalChunk = '';
      for (let index = event.resultIndex; index < event.results.length; index += 1) {
        const result = event.results[index];
        const text = result[0]?.transcript?.trim() || '';
        if (result.isFinal) finalChunk = append(finalChunk, text);
        else interim = append(interim, text);
      }
      if (finalChunk) this.committedTranscript = append(this.committedTranscript, finalChunk);
      this.interimTranscript = interim;
      this.emit();
    };
    recognition.onerror = (event) => {
      if (event.error === 'aborted' || event.error === 'no-speech') return;
      this.callbacks.onTerminalError(event.error === 'not-allowed' || event.error === 'service-not-allowed'
        ? 'Trình duyệt không có quyền sử dụng microphone.'
        : `Nhận dạng giọng nói đã dừng (${event.error}).`);
    };
    recognition.onend = () => {
      if (this.recognition === recognition) this.recognition = null;
      if (!this.active || this.paused || this.disposed) return;
      window.clearTimeout(this.restartTimer);
      this.restartTimer = window.setTimeout(() => this.startRecognition(), this.restartDelayMs);
    };
    this.recognition = recognition;
    try {
      recognition.start();
    } catch (error) {
      this.recognition = null;
      throw error;
    }
  }

  private abortRecognition() {
    const recognition = this.recognition;
    this.recognition = null;
    if (!recognition) return;
    try { recognition.abort(); } catch { /* already stopped */ }
  }

  private emit() {
    this.callbacks.onUpdate({
      committedTranscript: this.committedTranscript,
      interimTranscript: this.interimTranscript,
    });
  }
}

function append(existing: string, next: string) {
  return `${existing} ${next}`.replace(/\s+/g, ' ').trim();
}

function normalize(value: string) {
  return value.replace(/\s+/g, ' ').trim();
}

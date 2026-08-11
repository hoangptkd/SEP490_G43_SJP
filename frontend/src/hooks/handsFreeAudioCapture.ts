export interface CapturedAudioSegment {
  sequence: number;
  file: File;
  durationSeconds: number;
}

export interface CandidateAudioCaptureDependencies {
  getUserMedia: () => Promise<MediaStream>;
  createRecorder: (stream: MediaStream, options?: MediaRecorderOptions) => MediaRecorder;
  now: () => number;
}

const MIME_PREFERENCES = [
  'audio/webm;codecs=opus',
  'audio/webm',
  'audio/ogg;codecs=opus',
];

function defaultDependencies(): CandidateAudioCaptureDependencies {
  return {
    getUserMedia: () => navigator.mediaDevices.getUserMedia({ audio: true }),
    createRecorder: (stream, options) => new MediaRecorder(stream, options),
    now: () => Date.now(),
  };
}

export class CandidateAudioCapture {
  private stream?: MediaStream;
  private recorder?: MediaRecorder;
  private chunks: Blob[] = [];
  private sequence = -1;
  private startedAt = 0;
  private stopPromise?: Promise<CapturedAudioSegment | null>;
  private resolveStop?: (value: CapturedAudioSegment | null) => void;
  private rejectStop?: (reason: unknown) => void;

  constructor(private readonly dependencies = defaultDependencies()) {}

  get recording() {
    return this.recorder?.state === 'recording';
  }

  async startSegment(sequence: number): Promise<void> {
    if (this.recording) throw new Error('Một audio segment khác đang được ghi');
    if (!this.stream || this.stream.getTracks().every((track) => track.readyState === 'ended')) {
      this.stream = await this.dependencies.getUserMedia();
    }
    const mimeType = MIME_PREFERENCES.find((value) =>
      typeof MediaRecorder.isTypeSupported !== 'function' || MediaRecorder.isTypeSupported(value));
    this.chunks = [];
    this.sequence = sequence;
    this.startedAt = this.dependencies.now();
    const recorder = this.dependencies.createRecorder(this.stream, mimeType ? { mimeType } : undefined);
    this.recorder = recorder;
    this.stopPromise = new Promise((resolve, reject) => {
      this.resolveStop = resolve;
      this.rejectStop = reject;
    });
    recorder.ondataavailable = (event) => {
      if (event.data.size > 0) this.chunks.push(event.data);
    };
    recorder.onerror = () => {
      this.rejectStop?.(new Error('MediaRecorder không thể ghi audio'));
      this.resetRecorder();
    };
    recorder.onstop = () => {
      const type = recorder.mimeType || this.chunks[0]?.type || 'audio/webm';
      const blob = new Blob(this.chunks, { type });
      const extension = type.includes('ogg') ? 'ogg' : 'webm';
      const result = blob.size === 0 ? null : {
        sequence: this.sequence,
        file: new File([blob], `answer-segment-${this.sequence}.${extension}`, { type }),
        durationSeconds: Math.max(0.001, (this.dependencies.now() - this.startedAt) / 1000),
      };
      this.resolveStop?.(result);
      this.resetRecorder();
    };
    recorder.start();
  }

  async stopSegment(): Promise<CapturedAudioSegment | null> {
    const recorder = this.recorder;
    const pending = this.stopPromise;
    if (!recorder || !pending) return null;
    if (recorder.state !== 'inactive') recorder.stop();
    return pending;
  }

  abortSegment(): void {
    const recorder = this.recorder;
    if (recorder && recorder.state !== 'inactive') {
      recorder.onstop = () => {
        this.resolveStop?.(null);
        this.resetRecorder();
      };
      recorder.stop();
    } else {
      this.resolveStop?.(null);
      this.resetRecorder();
    }
  }

  dispose(): void {
    this.abortSegment();
    this.stream?.getTracks().forEach((track) => track.stop());
    this.stream = undefined;
  }

  private resetRecorder() {
    if (this.recorder) {
      this.recorder.ondataavailable = null;
      this.recorder.onerror = null;
      this.recorder.onstop = null;
    }
    this.recorder = undefined;
    this.stopPromise = undefined;
    this.resolveStop = undefined;
    this.rejectStop = undefined;
    this.chunks = [];
  }
}

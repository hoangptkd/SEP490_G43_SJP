import type { AiInterviewLiveTranscriptionSession } from '../types/aiInterview';

export type AnswerTranscriptionProviderKind = 'web_speech' | 'gladia_live';

export interface AnswerTranscriptionUpdate {
  committedTranscript: string;
  interimTranscript: string;
}

export interface AnswerTranscriptionCallbacks {
  onUpdate: (update: AnswerTranscriptionUpdate) => void;
  onTerminalError: (message: string) => void;
}

export interface AnswerTranscriptionResult {
  source: AnswerTranscriptionProviderKind;
  transcript: string;
  liveSessionToken?: string;
}

export interface AnswerTranscriptionProvider {
  readonly kind: AnswerTranscriptionProviderKind;
  start(stream: MediaStream, initialTranscript: string): Promise<void>;
  pause(): void;
  resume(): Promise<void>;
  snapshotTranscript(): string;
  appendExternalTranscript(text: string): void;
  finish(): Promise<AnswerTranscriptionResult>;
  abort(): void;
  dispose(): void;
}

export interface AnswerTranscriptionProviderDependencies {
  callbacks: AnswerTranscriptionCallbacks;
  createLiveSession?: (sampleRate: number) => Promise<AiInterviewLiveTranscriptionSession>;
  recognitionRestartDelayMs: number;
}

import type { AiInterviewTranscriptionTicket } from '../types/aiInterview';
import type {
  AnswerTranscriptionCallbacks,
  AnswerTranscriptionProvider,
  AnswerTranscriptionProviderKind,
} from './answerTranscriptionProvider';
import { SpeechmaticsRealtimeAnswerTranscriptionProvider } from './speechmaticsRealtimeAnswerTranscriptionProvider';
import { WebSpeechAnswerTranscriptionProvider } from './webSpeechAnswerTranscriptionProvider';

interface ProviderFactoryOptions {
  recognitionRestartDelayMs: number;
  createSpeechmaticsTicket?: () => Promise<AiInterviewTranscriptionTicket>;
}

export function createAnswerTranscriptionProvider(
  kind: AnswerTranscriptionProviderKind,
  callbacks: AnswerTranscriptionCallbacks,
  options: ProviderFactoryOptions,
): AnswerTranscriptionProvider {
  if (kind === 'speechmatics_realtime') {
    if (!options.createSpeechmaticsTicket) throw new Error('Thiếu hàm tạo Speechmatics ticket');
    return new SpeechmaticsRealtimeAnswerTranscriptionProvider(callbacks, options.createSpeechmaticsTicket);
  }
  return new WebSpeechAnswerTranscriptionProvider(callbacks, options.recognitionRestartDelayMs);
}

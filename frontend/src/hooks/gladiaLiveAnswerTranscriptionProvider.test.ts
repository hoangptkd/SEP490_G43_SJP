import { describe, expect, it, vi } from 'vitest';
import { GladiaLiveAnswerTranscriptionProvider } from './gladiaLiveAnswerTranscriptionProvider';

describe('GladiaLiveAnswerTranscriptionProvider', () => {
  it('keeps partial text interim and builds the answer only from ordered final utterances', () => {
    const onUpdate = vi.fn();
    const provider = new GladiaLiveAnswerTranscriptionProvider(
      { onUpdate, onTerminalError: vi.fn() },
      vi.fn(),
    );
    const receive = (message: object) => (
      provider as unknown as { handleMessage: (raw: unknown) => void }
    ).handleMessage(JSON.stringify(message));

    receive({
      type: 'transcript',
      data: { id: 'later', is_final: false, utterance: { text: 'Spring bút' } },
    });
    expect(onUpdate).toHaveBeenLastCalledWith({
      committedTranscript: '',
      interimTranscript: 'Spring bút',
    });

    receive({
      type: 'transcript',
      data: { id: 'later', is_final: true, utterance: { text: 'Spring Boot', start: 2 } },
    });
    receive({
      type: 'transcript',
      data: { id: 'earlier', is_final: true, utterance: { text: 'Em dùng', start: 1 } },
    });
    receive({
      type: 'transcript',
      data: { id: 'later', is_final: true, utterance: { text: 'Spring Boot và REST API', start: 2 } },
    });

    expect(onUpdate).toHaveBeenLastCalledWith({
      committedTranscript: 'Em dùng Spring Boot và REST API',
      interimTranscript: '',
    });
  });

  it('snapshots finalized utterances before appending a continued answer', () => {
    const onUpdate = vi.fn();
    const provider = new GladiaLiveAnswerTranscriptionProvider(
      { onUpdate, onTerminalError: vi.fn() },
      vi.fn(),
    );
    const receive = (message: object) => (
      provider as unknown as { handleMessage: (raw: unknown) => void }
    ).handleMessage(JSON.stringify(message));
    receive({
      type: 'transcript',
      data: { id: 'answer', is_final: true, utterance: { text: 'Em dùng Spring Boot', start: 1 } },
    });

    expect(provider.snapshotTranscript()).toBe('Em dùng Spring Boot');
    provider.appendExternalTranscript('và Redis để cache');

    expect(onUpdate).toHaveBeenLastCalledWith({
      committedTranscript: 'Em dùng Spring Boot và Redis để cache',
      interimTranscript: '',
    });
  });
});

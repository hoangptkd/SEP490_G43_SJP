import { describe, expect, it, vi } from 'vitest';
import { WebSpeechAnswerTranscriptionProvider } from './webSpeechAnswerTranscriptionProvider';

describe('WebSpeechAnswerTranscriptionProvider', () => {
  it('promotes the visible interim transcript before confirmation and keeps it when speech continues', () => {
    const onUpdate = vi.fn();
    const provider = new WebSpeechAnswerTranscriptionProvider(
      { onUpdate, onTerminalError: vi.fn() },
      100,
    );
    const state = provider as unknown as {
      committedTranscript: string;
      interimTranscript: string;
    };
    state.committedTranscript = 'Trong dự án';
    state.interimTranscript = 'em dùng Spring Boot';

    expect(provider.snapshotTranscript()).toBe('Trong dự án em dùng Spring Boot');
    expect(state.interimTranscript).toBe('');

    provider.appendExternalTranscript('và Redis để cache');

    expect(onUpdate).toHaveBeenLastCalledWith({
      committedTranscript: 'Trong dự án em dùng Spring Boot và Redis để cache',
      interimTranscript: '',
    });
  });
});

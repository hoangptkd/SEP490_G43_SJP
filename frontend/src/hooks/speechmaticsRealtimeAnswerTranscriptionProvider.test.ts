import { describe, expect, it, vi } from 'vitest';
import { SpeechmaticsRealtimeAnswerTranscriptionProvider } from './speechmaticsRealtimeAnswerTranscriptionProvider';

describe('SpeechmaticsRealtimeAnswerTranscriptionProvider', () => {
  it('keeps partial text interim and orders final transcript segments by time', () => {
    const onUpdate = vi.fn();
    const provider = new SpeechmaticsRealtimeAnswerTranscriptionProvider(
      { onUpdate, onTerminalError: vi.fn() },
      vi.fn(),
    );
    const receive = (message: object) => (
      provider as unknown as { handleMessage: (raw: unknown) => void }
    ).handleMessage(JSON.stringify(message));

    receive({
      message: 'AddPartialTranscript',
      metadata: { transcript: 'Spring bút', start_time: 2, end_time: 3 },
    });
    expect(onUpdate).toHaveBeenLastCalledWith({
      committedTranscript: '',
      interimTranscript: 'Spring bút',
    });

    receive({
      message: 'AddTranscript',
      metadata: { transcript: 'Spring Boot', start_time: 2, end_time: 3 },
    });
    receive({
      message: 'AddTranscript',
      metadata: { transcript: 'Em dùng', start_time: 1, end_time: 2 },
    });

    expect(onUpdate).toHaveBeenLastCalledWith({
      committedTranscript: 'Em dùng Spring Boot',
      interimTranscript: '',
    });
  });

  it('keeps a visible partial in a synchronous snapshot', () => {
    const provider = new SpeechmaticsRealtimeAnswerTranscriptionProvider(
      { onUpdate: vi.fn(), onTerminalError: vi.fn() },
      vi.fn(),
    );
    const receive = (message: object) => (
      provider as unknown as { handleMessage: (raw: unknown) => void }
    ).handleMessage(JSON.stringify(message));
    provider.appendExternalTranscript('Em dùng');
    receive({
      message: 'AddPartialTranscript',
      metadata: { transcript: 'Spring Boot', start_time: 1, end_time: 2 },
    });

    expect(provider.snapshotTranscript()).toBe('Em dùng Spring Boot');
  });
});

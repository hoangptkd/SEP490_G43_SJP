import { beforeEach, describe, expect, it, vi } from 'vitest';

const { post } = vi.hoisted(() => ({ post: vi.fn() }));

vi.mock('./api', () => ({ api: { post } }));

import { aiInterviewService } from './aiInterviewService';

describe('aiInterviewService.finalizeHandsFreeCapture', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('gửi capture theo turn và giữ turnId làm lifecycle target của voice hook', async () => {
    const captureId = '68f83442-a7b2-4629-bc13-1c764ef1d6c0';
    const audio = new File([new Blob(['turn-segment'], { type: 'audio/webm' })], 'turn-segment.webm', {
      type: 'audio/webm',
    });
    post.mockResolvedValueOnce({
      data: {
        captureId,
        captureVersion: 3,
        browserTranscript: 'Tôi dùng Spring Boot.',
        rawTranscript: 'Tôi dùng Spring Boot.',
        transcriptStatus: 'browser_fallback',
        dataQuality: 'AUDIO_BROWSER_TRANSCRIPT',
      },
    });

    const result = await aiInterviewService.finalizeHandsFreeTurnCapture(
      'session-1',
      'turn-7',
      captureId,
      3,
      [{ sequence: 0, file: audio, durationSeconds: 4.75 }],
      'Tôi dùng Spring Boot.',
    );

    const [url, body, config] = post.mock.calls[0] as [string, FormData, { headers: Record<string, string> }];
    expect(url).toBe('/candidate/ai-interviews/sessions/session-1/turns/turn-7/answer-capture');
    expect(body.get('captureId')).toBe(captureId);
    expect(body.get('captureVersion')).toBe('3');
    expect(body.get('browserTranscript')).toBe('Tôi dùng Spring Boot.');
    expect(body.get('transcriptionSource')).toBe('web_speech');
    expect(body.get('liveSessionToken')).toBeNull();
    expect(body.getAll('audioSegments')).toEqual([audio]);
    expect(body.getAll('segmentSequences')).toEqual(['0']);
    expect(body.getAll('durationSeconds')).toEqual(['4.75']);
    expect(config.headers).toEqual({
      'Content-Type': 'multipart/form-data',
      'Idempotency-Key': captureId,
    });
    expect(result.questionId).toBe('turn-7');
  });

  it('gửi FormData multipart cùng idempotency và metadata của từng audio segment', async () => {
    const captureId = 'd12d868e-8aaa-4cab-a184-3e2bb2b040de';
    const first = new File([new Blob(['segment-0'], { type: 'audio/webm' })], 'segment-0.webm', {
      type: 'audio/webm',
    });
    const second = new File([new Blob(['segment-1'], { type: 'audio/webm' })], 'segment-1.webm', {
      type: 'audio/webm',
    });
    post.mockResolvedValueOnce({
      data: {
        questionId: 'question-1',
        captureId,
        captureVersion: 2,
        browserTranscript: 'Câu trả lời realtime',
        gladiaTranscript: 'Câu trả lời đã chuẩn hóa.',
        rawTranscript: 'Câu trả lời đã chuẩn hóa.',
        transcriptStatus: 'standardized',
        dataQuality: 'AUDIO_GLADIA',
      },
    });

    await aiInterviewService.finalizeHandsFreeCapture(
      'session-1',
      'question-1',
      captureId,
      2,
      [
        { sequence: 0, file: first, durationSeconds: 1.25 },
        { sequence: 1, file: second, durationSeconds: 2.5 },
      ],
      'Câu trả lời realtime',
    );

    expect(post).toHaveBeenCalledOnce();
    const [url, body, config] = post.mock.calls[0] as [string, FormData, { headers: Record<string, string> }];
    expect(url).toBe('/candidate/ai-interviews/sessions/session-1/questions/question-1/answer-capture');
    expect(body).toBeInstanceOf(FormData);
    expect(body.get('captureId')).toBe(captureId);
    expect(body.get('captureVersion')).toBe('2');
    expect(body.get('browserTranscript')).toBe('Câu trả lời realtime');
    expect(body.get('transcriptionSource')).toBe('web_speech');
    expect(body.getAll('audioSegments')).toEqual([first, second]);
    expect(body.getAll('segmentSequences')).toEqual(['0', '1']);
    expect(body.getAll('durationSeconds')).toEqual(['1.25', '2.5']);
    expect(config.headers).toEqual({
      'Content-Type': 'multipart/form-data',
      'Idempotency-Key': captureId,
    });
  });

  it('khởi tạo Gladia Live bằng sample rate thực của AudioContext', async () => {
    const liveSession = {
      provider: 'gladia_live',
      sessionToken: '9b43e821-239e-437c-a662-7f7c0458eb26',
      jobId: 'gladia-job-1',
      websocketUrl: 'wss://api.gladia.io/v2/live?token=temporary',
      targetType: 'turn',
      targetId: 'turn-7',
    };
    post.mockResolvedValueOnce({ data: liveSession });

    const result = await aiInterviewService.createLiveTranscription('session-1', 48_000);

    expect(post).toHaveBeenCalledWith(
      '/candidate/ai-interviews/sessions/session-1/live-transcription',
      { sampleRate: 48_000 },
    );
    expect(result).toBe(liveSession);
  });

  it('gửi token chứng minh transcript Gladia Live khi hoàn tất capture', async () => {
    const captureId = '766a0891-1ee4-4c38-b52a-b26f32e5c610';
    const liveSessionToken = '632b4071-8361-4417-9215-e597c5a82e02';
    const audio = new File([new Blob(['segment'], { type: 'audio/webm' })], 'segment.webm', {
      type: 'audio/webm',
    });
    post.mockResolvedValueOnce({ data: { questionId: 'question-1', captureId, captureVersion: 1 } });

    await aiInterviewService.finalizeHandsFreeCapture(
      'session-1',
      'question-1',
      captureId,
      1,
      [{ sequence: 0, file: audio, durationSeconds: 2 }],
      'Em dùng Spring Boot.',
      { source: 'gladia_live', liveSessionToken },
    );

    const body = post.mock.calls[0][1] as FormData;
    expect(body.get('transcriptionSource')).toBe('gladia_live');
    expect(body.get('liveSessionToken')).toBe(liveSessionToken);
  });

  it('gửi riêng raw transcript và final transcript đã sửa khi xác nhận', async () => {
    post.mockResolvedValueOnce({ data: { id: 'session-1' } });

    await aiInterviewService.confirmAnswer('session-1', 'question-1', {
      rawTranscript: 'Em dùng spring bút.',
      finalTranscript: 'Em dùng Spring Boot.',
    });

    expect(post).toHaveBeenCalledWith(
      '/candidate/ai-interviews/sessions/session-1/questions/question-1/confirm',
      { rawTranscript: 'Em dùng spring bút.', finalTranscript: 'Em dùng Spring Boot.' },
    );
  });

  it('xác nhận turn với transcript, dialogue version và idempotency key', async () => {
    const session = { id: 'session-1', conversation: { version: 13 } };
    post.mockResolvedValueOnce({ data: session });

    const result = await aiInterviewService.confirmTurn(
      'session-1',
      'turn-7',
      'b43cd77d-302f-4ac2-aed9-412f0168a1e8',
      {
        rawTranscript: 'Em dùng spring bút.',
        finalTranscript: 'Em dùng Spring Boot.',
      },
      12,
    );

    expect(post).toHaveBeenCalledWith(
      '/candidate/ai-interviews/sessions/session-1/turns/turn-7/confirm',
      {
        rawTranscript: 'Em dùng spring bút.',
        finalTranscript: 'Em dùng Spring Boot.',
        expectedDialogueVersion: 12,
      },
      { headers: { 'Idempotency-Key': 'b43cd77d-302f-4ac2-aed9-412f0168a1e8' } },
    );
    expect(result).toBe(session);
  });

  it('skip turn với dialogue version và idempotency key', async () => {
    const session = { id: 'session-1', conversation: { version: 9 } };
    post.mockResolvedValueOnce({ data: session });

    const result = await aiInterviewService.skipTurn(
      'session-1',
      'turn-4',
      'ed037dd2-c4c1-41f9-a199-56c62f25baa6',
      8,
    );

    expect(post).toHaveBeenCalledWith(
      '/candidate/ai-interviews/sessions/session-1/turns/turn-4/skip',
      { expectedDialogueVersion: 8 },
      { headers: { 'Idempotency-Key': 'ed037dd2-c4c1-41f9-a199-56c62f25baa6' } },
    );
    expect(result).toBe(session);
  });

  it('ghi nhận đọc lại turn theo dialogue version hiện tại', async () => {
    const session = { id: 'session-1', conversation: { version: 6 } };
    post.mockResolvedValueOnce({ data: session });

    const result = await aiInterviewService.replayTurn('session-1', 'turn-3', 5);

    expect(post).toHaveBeenCalledWith(
      '/candidate/ai-interviews/sessions/session-1/turns/turn-3/replay',
      { expectedDialogueVersion: 5 },
    );
    expect(result).toBe(session);
  });

  it('retry conversation không gửi lại transcript của câu trước', async () => {
    const session = { id: 'session-1', conversation: { version: 15 } };
    post.mockResolvedValueOnce({ data: session });

    const result = await aiInterviewService.retryConversation('session-1');

    expect(post).toHaveBeenCalledWith(
      '/candidate/ai-interviews/sessions/session-1/conversation/retry',
    );
    expect(result).toBe(session);
  });
});

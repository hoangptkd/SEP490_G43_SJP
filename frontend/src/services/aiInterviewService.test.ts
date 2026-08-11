import { beforeEach, describe, expect, it, vi } from 'vitest';

const { post } = vi.hoisted(() => ({ post: vi.fn() }));

vi.mock('./api', () => ({ api: { post } }));

import { aiInterviewService } from './aiInterviewService';

describe('aiInterviewService.finalizeHandsFreeCapture', () => {
  beforeEach(() => {
    vi.clearAllMocks();
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
        finalTranscript: 'Câu trả lời đã chuẩn hóa.',
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
    expect(body.getAll('audioSegments')).toEqual([first, second]);
    expect(body.getAll('segmentSequences')).toEqual(['0', '1']);
    expect(body.getAll('durationSeconds')).toEqual(['1.25', '2.5']);
    expect(config.headers).toEqual({
      'Content-Type': 'multipart/form-data',
      'Idempotency-Key': captureId,
    });
  });
});

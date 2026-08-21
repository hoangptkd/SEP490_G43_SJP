import { beforeEach, describe, expect, it, vi } from 'vitest';

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock('./api', () => ({ api: { get, post } }));

import { interviewService } from './interviewService';

describe('interviewService', () => {
  beforeEach(() => vi.clearAllMocks());

  it('getAllByApplication calls GET /interviews/application/:id', async () => {
    get.mockResolvedValueOnce({ data: [{ id: 1 }] });
    const result = await interviewService.getAllByApplication(10);
    expect(get).toHaveBeenCalledWith('/interviews/application/10');
    expect(result).toHaveLength(1);
  });

  it('getById calls GET /interviews/:id', async () => {
    get.mockResolvedValueOnce({ data: { id: 1, status: 'SCHEDULED' } });
    const result = await interviewService.getById(1);
    expect(get).toHaveBeenCalledWith('/interviews/1');
    expect(result.status).toBe('SCHEDULED');
  });

  it('create calls POST /interviews', async () => {
    const data = { applicationId: 1, type: 'AI_INTERVIEW' as const, scheduledAt: '2025-01-01', durationMinutes: 30 };
    post.mockResolvedValueOnce({ data: { id: 1 } });
    const result = await interviewService.create(data);
    expect(post).toHaveBeenCalledWith('/interviews', data);
    expect(result.id).toBe(1);
  });

  it('submitResponse calls POST /interviews/:id/responses', async () => {
    const data = { questionId: 1, answerText: 'My answer' };
    post.mockResolvedValueOnce({ data: { id: 1 } });
    await interviewService.submitResponse(1, data);
    expect(post).toHaveBeenCalledWith('/interviews/1/responses', data);
  });

  it('getFeedback calls GET /interviews/:id/feedback', async () => {
    get.mockResolvedValueOnce({ data: { score: 90 } });
    const result = await interviewService.getFeedback(1);
    expect(get).toHaveBeenCalledWith('/interviews/1/feedback');
    expect(result.score).toBe(90);
  });

  it('getQuestions calls GET /interviews/:id/questions', async () => {
    get.mockResolvedValueOnce({ data: [{ id: 1, text: 'Q1' }] });
    const result = await interviewService.getQuestions(1);
    expect(get).toHaveBeenCalledWith('/interviews/1/questions');
    expect(result).toHaveLength(1);
  });
});

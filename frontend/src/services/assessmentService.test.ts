import { beforeEach, describe, expect, it, vi } from 'vitest';

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock('./api', () => ({ api: { get, post } }));

import { assessmentService } from './assessmentService';

describe('assessmentService', () => {
  beforeEach(() => vi.clearAllMocks());

  it('getByJob calls GET /assessments/job/:id', async () => {
    get.mockResolvedValueOnce({ data: [{ id: 1, title: 'Quiz' }] });
    const result = await assessmentService.getByJob(1);
    expect(get).toHaveBeenCalledWith('/assessments/job/1');
    expect(result).toHaveLength(1);
  });

  it('getByCandidate calls GET /assessments/candidate/:id', async () => {
    get.mockResolvedValueOnce({ data: [] });
    const result = await assessmentService.getByCandidate(5);
    expect(get).toHaveBeenCalledWith('/assessments/candidate/5');
    expect(result).toHaveLength(0);
  });

  it('getById calls GET /assessments/:id', async () => {
    get.mockResolvedValueOnce({ data: { id: 1, title: 'Test' } });
    const result = await assessmentService.getById(1);
    expect(get).toHaveBeenCalledWith('/assessments/1');
    expect(result.title).toBe('Test');
  });

  it('create calls POST /assessments', async () => {
    const data = { title: 'Quiz', type: 'QUIZ' as const, questions: [], durationMinutes: 30 };
    post.mockResolvedValueOnce({ data: { id: 1, ...data } });
    const result = await assessmentService.create(data);
    expect(post).toHaveBeenCalledWith('/assessments', data);
    expect(result.id).toBe(1);
  });

  it('submit calls POST /assessments/:id/submit', async () => {
    const data = { answers: [{ questionId: 1, answer: 'A' }] };
    post.mockResolvedValueOnce({ data: { score: 85 } });
    const result = await assessmentService.submit(1, data);
    expect(post).toHaveBeenCalledWith('/assessments/1/submit', data);
    expect(result.score).toBe(85);
  });
});

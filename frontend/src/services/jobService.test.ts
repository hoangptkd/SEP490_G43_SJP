import { beforeEach, describe, expect, it, vi } from 'vitest';

const { get } = vi.hoisted(() => ({ get: vi.fn() }));

vi.mock('./api', () => ({ api: { get } }));

import { jobService } from './jobService';

describe('jobService', () => {
  beforeEach(() => vi.clearAllMocks());

  it('getCategories calls GET /categories', async () => {
    get.mockResolvedValueOnce({ data: [{ id: '1', name: 'IT' }] });
    const result = await jobService.getCategories();
    expect(get).toHaveBeenCalledWith('/categories');
    expect(result).toHaveLength(1);
    expect(result[0].name).toBe('IT');
  });

  it('getAll calls GET /jobs with filters', async () => {
    get.mockResolvedValueOnce({ data: { items: [], totalPages: 0 } });
    await jobService.getAll({ search: 'dev', location: 'HN' }, 0, 10);
    const [url] = get.mock.calls[0] as [string, unknown];
    expect(url).toContain('/jobs');
    expect(url).toContain('search=dev');
    expect(url).toContain('location=HN');
  });

  it('getAll omits undefined filter values', async () => {
    get.mockResolvedValueOnce({ data: { items: [] } });
    await jobService.getAll({}, 0, 10);
    const [url] = get.mock.calls[0] as [string, unknown];
    expect(url).not.toContain('search=');
    expect(url).not.toContain('location=');
  });

  it('getById calls GET /jobs/:id', async () => {
    get.mockResolvedValueOnce({ data: { id: 'j-1', title: 'Dev' } });
    const result = await jobService.getById('j-1');
    expect(get).toHaveBeenCalledWith('/jobs/j-1');
    expect(result.title).toBe('Dev');
  });

  it('getCompany calls GET /companies/:id', async () => {
    get.mockResolvedValueOnce({ data: { id: 'c-1', name: 'Corp' } });
    const result = await jobService.getCompany('c-1', 0, 5);
    expect(get).toHaveBeenCalledWith('/companies/c-1', { params: { page: 0, size: 5 } });
    expect(result.name).toBe('Corp');
  });

  it('recommendations calls GET /candidate/recommendations/jobs', async () => {
    get.mockResolvedValueOnce({ data: [{ jobId: '1' }] });
    const result = await jobService.recommendations();
    expect(get).toHaveBeenCalledWith('/candidate/recommendations/jobs');
    expect(result).toHaveLength(1);
  });
});

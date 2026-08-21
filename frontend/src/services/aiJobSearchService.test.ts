import { beforeEach, describe, expect, it, vi } from 'vitest';

const { get, post, delete: del } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  delete: vi.fn(),
}));

vi.mock('./api', () => ({ api: { get, post, delete: del } }));

import { aiJobSearchService } from './aiJobSearchService';

describe('aiJobSearchService', () => {
  beforeEach(() => vi.clearAllMocks());

  it('status calls GET /candidate/ai-job-search/status', async () => {
    get.mockResolvedValueOnce({ data: { consented: true } });
    const result = await aiJobSearchService.status();
    expect(get).toHaveBeenCalledWith('/candidate/ai-job-search/status', { signal: undefined });
    expect(result.consented).toBe(true);
  });

  it('consent calls POST /candidate/ai-job-search/consent', async () => {
    post.mockResolvedValueOnce({ data: { consented: true } });
    const result = await aiJobSearchService.consent('v1.0');
    expect(post).toHaveBeenCalledWith('/candidate/ai-job-search/consent', {
      accepted: true,
      policyVersion: 'v1.0',
    });
    expect(result.consented).toBe(true);
  });

  it('revokeConsent calls DELETE /candidate/ai-job-search/consent', async () => {
    del.mockResolvedValueOnce({});
    await aiJobSearchService.revokeConsent();
    expect(del).toHaveBeenCalledWith('/candidate/ai-job-search/consent');
  });

  it('search calls POST /candidate/ai-job-search/search with default forceRefresh', async () => {
    post.mockResolvedValueOnce({ data: { jobs: [] } });
    await aiJobSearchService.search();
    expect(post).toHaveBeenCalledWith('/candidate/ai-job-search/search', { forceRefresh: false });
  });

  it('search calls POST with forceRefresh=true', async () => {
    post.mockResolvedValueOnce({ data: { jobs: [] } });
    await aiJobSearchService.search(true);
    expect(post).toHaveBeenCalledWith('/candidate/ai-job-search/search', { forceRefresh: true });
  });

  it('recommendation calls GET with runId and jobId', async () => {
    get.mockResolvedValueOnce({ data: { jobId: 'j-1', score: 0.9 } });
    const result = await aiJobSearchService.recommendation('run-1', 'job-1');
    expect(get).toHaveBeenCalledWith('/candidate/ai-job-search/runs/run-1/jobs/job-1');
    expect(result.score).toBe(0.9);
  });
});

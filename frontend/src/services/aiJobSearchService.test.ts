import { beforeEach, describe, expect, it, vi } from 'vitest';

const { get, post, delete: del } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  delete: vi.fn(),
}));

vi.mock('./api', () => ({ api: { get, post, delete: del } }));

import { aiJobSearchService } from './aiJobSearchService';

describe('aiJobSearchService', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('status calls GET /candidate/ai-job-search/status', async () => {
    get.mockResolvedValueOnce({ data: { consentRequired: false } });
    const result = await aiJobSearchService.status();
    expect(get).toHaveBeenCalledWith('/candidate/ai-job-search/status', { signal: undefined });
    expect(result.consentRequired).toBe(false);
  });

  it('consent calls POST /candidate/ai-job-search/consent', async () => {
    post.mockResolvedValueOnce({ data: { consentRequired: false } });
    const result = await aiJobSearchService.consent('v1.0');
    expect(post).toHaveBeenCalledWith('/candidate/ai-job-search/consent', {
      accepted: true,
      policyVersion: 'v1.0',
    });
    expect(result.consentRequired).toBe(false);
  });

  it('revokeConsent calls DELETE /candidate/ai-job-search/consent', async () => {
    del.mockResolvedValueOnce({});
    await aiJobSearchService.revokeConsent();
    expect(del).toHaveBeenCalledWith('/candidate/ai-job-search/consent');
  });

  it('search calls POST /candidate/ai-job-search/search with default forceRefresh', async () => {
    post.mockResolvedValueOnce({ data: { jobs: [] } });
    await aiJobSearchService.search({ cvId: 'cv-a', filters: {} });
    expect(post).toHaveBeenCalledWith('/candidate/ai-job-search/search', { cvId: 'cv-a', filters: {}, forceRefresh: false }, { signal: undefined });
  });

  it('search calls POST with forceRefresh=true', async () => {
    post.mockResolvedValueOnce({ data: { jobs: [] } });
    const signal = new AbortController().signal;
    await aiJobSearchService.search({ cvId: 'cv-b', filters: { location: 'Hà Nội' } }, true, signal);
    expect(post).toHaveBeenCalledWith('/candidate/ai-job-search/search', {
      cvId: 'cv-b', filters: { location: 'Hà Nội' }, forceRefresh: true,
    }, { signal });
  });

  it('recommendation calls GET with runId and jobId', async () => {
    get.mockResolvedValueOnce({ data: { jobId: 'j-1', matchScore: 90 } });
    const result = await aiJobSearchService.recommendation('run-1', 'job-1');
    expect(get).toHaveBeenCalledWith('/candidate/ai-job-search/runs/run-1/jobs/job-1');
    expect(result.matchScore).toBe(90);
  });

  it('returns Profile fallback through the same search request without retrying or requesting an AI run', async () => {
    const fallback = {
      source: 'PROFILE_FALLBACK', cvId: 'cv-a', runId: null, cached: false,
      quota: { used: 1, limit: 3, remaining: 2 },
      items: [{ rank: 1, matchScore: 72, matchedSkills: ['Java'], missingSkills: [], evidence: [] }],
    };
    post.mockResolvedValueOnce({ data: fallback });
    const response = await aiJobSearchService.search({ cvId: 'cv-a', filters: {} });
    expect(response).toEqual(fallback);
    expect(post).toHaveBeenCalledTimes(1);
    expect(get).not.toHaveBeenCalled();
  });
});

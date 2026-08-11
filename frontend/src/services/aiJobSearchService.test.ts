import { beforeEach, describe, expect, it, vi } from 'vitest';

const { get, post, del } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  del: vi.fn(),
}));

vi.mock('./api', () => ({ api: { get, post, delete: del } }));

import { aiJobSearchService } from './aiJobSearchService';

describe('aiJobSearchService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('dùng đúng API status và search', async () => {
    get.mockResolvedValueOnce({ data: { enabled: true } });
    post.mockResolvedValueOnce({ data: { source: 'AI', items: [] } });

    await aiJobSearchService.status();
    await aiJobSearchService.search(true);

    expect(get).toHaveBeenCalledWith('/candidate/ai-job-search/status', { signal: undefined });
    expect(post).toHaveBeenCalledWith('/candidate/ai-job-search/search', { forceRefresh: true });
  });

  it('gửi policy version khi xác nhận consent', async () => {
    post.mockResolvedValueOnce({ data: { consentRequired: false } });

    await aiJobSearchService.consent('ai-job-search-v1');

    expect(post).toHaveBeenCalledWith('/candidate/ai-job-search/consent', {
      accepted: true,
      policyVersion: 'ai-job-search-v1',
    });
  });
});

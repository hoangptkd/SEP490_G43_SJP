import { beforeEach, describe, expect, it, vi } from 'vitest';

const { get, post, put, patch, delete: del } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  patch: vi.fn(),
  delete: vi.fn(),
}));

vi.mock('./api', () => ({ api: { get, post, put, patch, delete: del } }));

import { employerService } from './employerService';

describe('employerService', () => {
  beforeEach(() => vi.clearAllMocks());

  it('getCompanyProfile calls GET /employer/company', async () => {
    get.mockResolvedValueOnce({ data: { name: 'Corp' } });
    const result = await employerService.getCompanyProfile();
    expect(get).toHaveBeenCalledWith('/employer/company');
    expect(result.name).toBe('Corp');
  });

  it('getDashboardStats calls GET /employer/dashboard', async () => {
    get.mockResolvedValueOnce({ data: { totalJobs: 5 } });
    const result = await employerService.getDashboardStats();
    expect(get).toHaveBeenCalledWith('/employer/dashboard', { params: {} });
    expect(result.totalJobs).toBe(5);
  });

  it('getDashboardStats with dates passes params', async () => {
    get.mockResolvedValueOnce({ data: { totalJobs: 3 } });
    await employerService.getDashboardStats('2025-01-01', '2025-12-31');
    expect(get).toHaveBeenCalledWith('/employer/dashboard', {
      params: { startDate: '2025-01-01', endDate: '2025-12-31' },
    });
  });

  it('getInterviews calls GET /employer/interviews', async () => {
    get.mockResolvedValueOnce({ data: [] });
    await employerService.getInterviews('today');
    expect(get).toHaveBeenCalledWith('/employer/interviews', { params: { range: 'today' } });
  });

  it('updateCompanyProfile calls PUT /employer/company', async () => {
    put.mockResolvedValueOnce({ data: { name: 'Updated' } });
    const result = await employerService.updateCompanyProfile({ name: 'Updated' } as any);
    expect(put).toHaveBeenCalledWith('/employer/company', { name: 'Updated' });
    expect(result.name).toBe('Updated');
  });

  it('getLocations calls GET /employer/company/locations', async () => {
    get.mockResolvedValueOnce({ data: [{ id: '1' }] });
    const result = await employerService.getLocations();
    expect(get).toHaveBeenCalledWith('/employer/company/locations');
    expect(result).toHaveLength(1);
  });

  it('deleteLocation calls DELETE', async () => {
    del.mockResolvedValueOnce({});
    await employerService.deleteLocation('loc-1');
    expect(del).toHaveBeenCalledWith('/employer/company/locations/loc-1');
  });

  it('getJobs calls GET /employer/jobs', async () => {
    get.mockResolvedValueOnce({ data: { items: [] } });
    await employerService.getJobs({ status: 'active', page: 1 });
    expect(get).toHaveBeenCalledWith('/employer/jobs', { params: { status: 'active', page: 1 } });
  });

  it('createJob calls POST /employer/jobs', async () => {
    post.mockResolvedValueOnce({ data: { id: 'j-1' } });
    await employerService.createJob({ title: 'Dev' } as any);
    expect(post).toHaveBeenCalledWith('/employer/jobs', { title: 'Dev' });
  });

  it('deleteJob calls DELETE /employer/jobs/:id', async () => {
    del.mockResolvedValueOnce({});
    await employerService.deleteJob('j-1');
    expect(del).toHaveBeenCalledWith('/employer/jobs/j-1');
  });

  it('closeJob calls POST /employer/jobs/:id/close', async () => {
    post.mockResolvedValueOnce({ data: { status: 'closed' } });
    await employerService.closeJob('j-1');
    expect(post).toHaveBeenCalledWith('/employer/jobs/j-1/close');
  });

  it('markNotificationRead calls PATCH', async () => {
    patch.mockResolvedValueOnce({});
    await employerService.markNotificationRead('n-1');
    expect(patch).toHaveBeenCalledWith('/employer/notifications/n-1/read');
  });

  it('markAllNotificationsRead calls PATCH', async () => {
    patch.mockResolvedValueOnce({});
    await employerService.markAllNotificationsRead();
    expect(patch).toHaveBeenCalledWith('/employer/notifications/read-all');
  });

  it('scheduleInterview calls POST /v1/applications/:id/interviews', async () => {
    post.mockResolvedValueOnce({ data: { id: 'int-1' } });
    const data = { scheduledAt: '2025-01-01', type: 'ONLINE' };
    await employerService.scheduleInterview('app-1', data as any);
    expect(post).toHaveBeenCalledWith('/v1/applications/app-1/interviews', data);
  });

  it('rejectApplication calls POST /v1/applications/:id/reject', async () => {
    post.mockResolvedValueOnce({});
    await employerService.rejectApplication('app-1', 'not fit');
    expect(post).toHaveBeenCalledWith('/v1/applications/app-1/reject', null, { params: { note: 'not fit' } });
  });

  it('getAiRankingQuota calls GET /employer/ai-ranking-quota', async () => {
    get.mockResolvedValueOnce({ data: { used: 5, limit: 100, remaining: 95, isUnlimited: false } });
    const result = await employerService.getAiRankingQuota();
    expect(result.remaining).toBe(95);
  });
});

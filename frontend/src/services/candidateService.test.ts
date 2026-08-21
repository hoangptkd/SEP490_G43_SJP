import { beforeEach, describe, expect, it, vi } from 'vitest';

const { get, post, put, patch, delete: del } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  patch: vi.fn(),
  delete: vi.fn(),
}));

vi.mock('./api', () => ({ api: { get, post, put, patch, delete: del } }));

import { candidateService } from './candidateService';

describe('candidateService', () => {
  beforeEach(() => vi.clearAllMocks());

  it('getProfile calls GET /candidate/profile', async () => {
    get.mockResolvedValueOnce({ data: { fullName: 'Nguyen Van A' } });
    const result = await candidateService.getProfile();
    expect(get).toHaveBeenCalledWith('/candidate/profile');
    expect(result.fullName).toBe('Nguyen Van A');
  });

  it('updateProfile calls PUT /candidate/profile', async () => {
    const profile = { fullName: 'Updated' };
    put.mockResolvedValueOnce({ data: profile });
    const result = await candidateService.updateProfile(profile);
    expect(put).toHaveBeenCalledWith('/candidate/profile', profile);
    expect(result.fullName).toBe('Updated');
  });

  it('getOnboarding calls GET /candidate/onboarding', async () => {
    get.mockResolvedValueOnce({ data: { completed: false } });
    const result = await candidateService.getOnboarding();
    expect(get).toHaveBeenCalledWith('/candidate/onboarding');
    expect(result.completed).toBe(false);
  });

  it('skipOnboarding calls POST /candidate/onboarding/skip', async () => {
    post.mockResolvedValueOnce({ data: { completed: true } });
    const result = await candidateService.skipOnboarding();
    expect(post).toHaveBeenCalledWith('/candidate/onboarding/skip');
    expect(result.completed).toBe(true);
  });

  it('getCvs calls GET /candidate/cvs with pagination', async () => {
    get.mockResolvedValueOnce({ data: { items: [], totalPages: 0 } });
    await candidateService.getCvs(1, 5);
    expect(get).toHaveBeenCalledWith('/candidate/cvs', { params: { page: 1, size: 5 } });
  });

  it('deleteCv calls DELETE /candidate/cvs/:id', async () => {
    del.mockResolvedValueOnce({});
    await candidateService.deleteCv('cv-1');
    expect(del).toHaveBeenCalledWith('/candidate/cvs/cv-1');
  });

  it('setDefaultCv calls PATCH /candidate/cvs/:id/default', async () => {
    patch.mockResolvedValueOnce({ data: { id: 'cv-1', isDefault: true } });
    const result = await candidateService.setDefaultCv('cv-1');
    expect(patch).toHaveBeenCalledWith('/candidate/cvs/cv-1/default');
    expect(result.isDefault).toBe(true);
  });

  it('saveJob calls POST /candidate/saved-jobs/:id', async () => {
    post.mockResolvedValueOnce({});
    await candidateService.saveJob('job-1');
    expect(post).toHaveBeenCalledWith('/candidate/saved-jobs/job-1');
  });

  it('unsaveJob calls DELETE /candidate/saved-jobs/:id', async () => {
    del.mockResolvedValueOnce({});
    await candidateService.unsaveJob('job-1');
    expect(del).toHaveBeenCalledWith('/candidate/saved-jobs/job-1');
  });

  it('apply calls POST /applications', async () => {
    post.mockResolvedValueOnce({ data: { id: 'app-1' } });
    const result = await candidateService.apply('job-1', 'cv-1');
    expect(post).toHaveBeenCalledWith('/applications', {
      jobId: 'job-1',
      cvId: 'cv-1',
      cvVersionId: undefined,
      preferredLocation: undefined,
      coverLetter: undefined,
    });
    expect(result.id).toBe('app-1');
  });

  it('getApplications calls GET /applications/me', async () => {
    get.mockResolvedValueOnce({ data: { items: [] } });
    await candidateService.getApplications(0, 5);
    expect(get).toHaveBeenCalledWith('/applications/me', { params: { page: 0, size: 5 } });
  });

  it('markNotificationRead calls PATCH', async () => {
    patch.mockResolvedValueOnce({});
    await candidateService.markNotificationRead('n-1');
    expect(patch).toHaveBeenCalledWith('/candidate/notifications/n-1/read');
  });

  it('markAllNotificationsRead calls PATCH', async () => {
    patch.mockResolvedValueOnce({});
    await candidateService.markAllNotificationsRead();
    expect(patch).toHaveBeenCalledWith('/candidate/notifications/read-all');
  });

  it('getSubscription calls GET /candidate/subscription', async () => {
    get.mockResolvedValueOnce({ data: { planName: 'Free' } });
    const result = await candidateService.getSubscription();
    expect(get).toHaveBeenCalledWith('/candidate/subscription');
    expect(result.planName).toBe('Free');
  });

  it('respondToInterview calls PUT /v1/interviews/:id/candidate-response', async () => {
    put.mockResolvedValueOnce({ data: { status: 'ACCEPTED' } });
    await candidateService.respondToInterview('int-1', 'ACCEPT');
    expect(put).toHaveBeenCalledWith('/v1/interviews/int-1/candidate-response', { response: 'ACCEPT', rescheduleNote: undefined });
  });

  it('respondToOffer sends correct decision', async () => {
    put.mockResolvedValueOnce({ data: { status: 'ACCEPTED' } });
    await candidateService.respondToOffer('offer-1', true, 'thanks');
    expect(put).toHaveBeenCalledWith('/v1/offers/offer-1/response', { decision: 'ACCEPT', note: 'thanks' });
  });
});

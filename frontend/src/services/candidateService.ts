import { api } from './api';
import type {
  CandidateApplication,
  CandidateProfile,
  CvFile,
  CvVersion,
  NotificationItem,
  PageResult,
  JobAlert,
  JobAlertInput,
  SubscriptionView,
  CandidateOnboarding,
  CandidateOnboardingInput,
} from '../types/candidateDomain';
import type { Job } from '../types/job';

export const candidateService = {
  getProfile: async (): Promise<CandidateProfile> => {
    const response = await api.get<CandidateProfile>('/candidate/profile');
    return response.data;
  },

  updateProfile: async (profile: Partial<CandidateProfile>): Promise<CandidateProfile> => {
    const response = await api.put<CandidateProfile>('/candidate/profile', profile);
    return response.data;
  },

  getOnboarding: async (): Promise<CandidateOnboarding> => {
    const response = await api.get<CandidateOnboarding>('/candidate/onboarding');
    return response.data;
  },

  completeOnboarding: async (input: CandidateOnboardingInput): Promise<CandidateOnboarding> => {
    const response = await api.put<CandidateOnboarding>('/candidate/onboarding', input);
    return response.data;
  },

  skipOnboarding: async (): Promise<CandidateOnboarding> => {
    const response = await api.post<CandidateOnboarding>('/candidate/onboarding/skip');
    return response.data;
  },

  getJobTitleSuggestions: async (query = '', size = 10): Promise<string[]> => {
    const response = await api.get<string[]>('/candidate/onboarding/job-title-suggestions', {
      params: { query, size },
    });
    return response.data;
  },

  getCvs: async (page = 0, size = 20): Promise<PageResult<CvFile>> => {
    const response = await api.get<PageResult<CvFile>>('/candidate/cvs', { params: { page, size } });
    return response.data;
  },

  uploadCv: async (file: File): Promise<CvFile> => {
    const data = new FormData();
    data.append('file', file);
    const response = await api.post<CvFile>('/candidate/cvs', data, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return response.data;
  },

  setDefaultCv: async (id: string): Promise<CvFile> => {
    const response = await api.patch<CvFile>(`/candidate/cvs/${id}/default`);
    return response.data;
  },

  deleteCv: async (id: string): Promise<void> => {
    await api.delete(`/candidate/cvs/${id}`);
  },

  downloadCv: async (id: string): Promise<Blob> => {
    const response = await api.get<Blob>(`/candidate/cvs/${id}/download`, { responseType: 'blob' });
    return response.data;
  },

  getCvVersions: async (page = 0, size = 20): Promise<PageResult<CvVersion>> => {
    const response = await api.get<PageResult<CvVersion>>('/candidate/cv-versions', { params: { page, size } });
    return response.data;
  },

  createCvVersion: async (title: string, snapshot: Record<string, unknown>, templateKey = 'classic'): Promise<CvVersion> => {
    const response = await api.post<CvVersion>('/candidate/cv-versions', { title, templateKey, snapshot });
    return response.data;
  },

  updateCvVersion: async (
    id: string,
    title: string,
    snapshot: Record<string, unknown>,
    templateKey = 'classic',
  ): Promise<CvVersion> => {
    const response = await api.put<CvVersion>(`/candidate/cv-versions/${id}`, { title, templateKey, snapshot });
    return response.data;
  },

  deleteCvVersion: async (id: string): Promise<void> => {
    await api.delete(`/candidate/cv-versions/${id}`);
  },

  getSavedJobs: async (page = 0, size = 12): Promise<PageResult<Job>> => {
    const response = await api.get<PageResult<Job>>('/candidate/saved-jobs', { params: { page, size } });
    return response.data;
  },

  saveJob: async (jobId: string): Promise<void> => {
    await api.post(`/candidate/saved-jobs/${jobId}`);
  },

  unsaveJob: async (jobId: string): Promise<void> => {
    await api.delete(`/candidate/saved-jobs/${jobId}`);
  },

  apply: async (
    jobId: string,
    cvId?: string,
    cvVersionId?: string,
    preferredLocation?: string,
    coverLetter?: string,
  ): Promise<CandidateApplication> => {
    const response = await api.post<CandidateApplication>('/applications', {
      jobId,
      cvId,
      cvVersionId,
      preferredLocation,
      coverLetter,
    });
    return response.data;
  },

  getApplications: async (page = 0, size = 10): Promise<PageResult<CandidateApplication>> => {
    const response = await api.get<PageResult<CandidateApplication>>('/applications/me', { params: { page, size } });
    return response.data;
  },

  getApplication: async (id: string): Promise<CandidateApplication> => {
    const response = await api.get<CandidateApplication>(`/applications/me/${id}`);
    return response.data;
  },

  downloadSubmittedResume: async (applicationId: string): Promise<Blob> => {
    const response = await api.get<Blob>(`/applications/me/${applicationId}/resume`, { responseType: 'blob' });
    return response.data;
  },

  getNotifications: async (page = 0, size = 20): Promise<PageResult<NotificationItem>> => {
    const response = await api.get<PageResult<NotificationItem>>('/candidate/notifications', { params: { page, size } });
    return response.data;
  },

  markNotificationRead: async (id: string): Promise<void> => {
    await api.patch(`/candidate/notifications/${id}/read`);
  },

  markAllNotificationsRead: async (): Promise<void> => {
    await api.patch('/candidate/notifications/read-all');
  },

  getJobAlerts: async (page = 0, size = 10): Promise<PageResult<JobAlert>> => {
    const response = await api.get<PageResult<JobAlert>>('/candidate/job-alerts', { params: { page, size } });
    return response.data;
  },

  createJobAlert: async (data: JobAlertInput): Promise<JobAlert> => {
    const response = await api.post<JobAlert>('/candidate/job-alerts', data);
    return response.data;
  },

  updateJobAlert: async (id: string, data: JobAlertInput): Promise<JobAlert> => {
    const response = await api.put<JobAlert>(`/candidate/job-alerts/${id}`, data);
    return response.data;
  },

  deleteJobAlert: async (id: string): Promise<void> => {
    await api.delete(`/candidate/job-alerts/${id}`);
  },

  getSubscription: async (): Promise<SubscriptionView> => {
    const response = await api.get<SubscriptionView>('/candidate/subscription');
    return response.data;
  },

  reportJob: async (jobId: string, reason: string, description?: string): Promise<void> => {
    await api.post(`/jobs/${jobId}/reports`, { reason, description: description || '' });
  },

  respondToInterview: async (interviewId: string, responseStatus: string, rescheduleNote?: string): Promise<import('../types/candidateDomain').InterviewScheduleResponse> => {
    const response = await api.put(`/v1/interviews/${interviewId}/candidate-response`, { response: responseStatus, rescheduleNote });
    return response.data;
  },

  viewInterview: async (interviewId: string): Promise<import('../types/candidateDomain').InterviewScheduleResponse> => {
    const response = await api.put(`/v1/interviews/${interviewId}/view`);
    return response.data;
  },

  respondToOffer: async (offerId: string, accepted: boolean, note?: string): Promise<import('../types/candidateDomain').JobOfferResponse> => {
    const response = await api.put(`/v1/offers/${offerId}/response`, {
      decision: accepted ? 'ACCEPT' : 'REJECT',
      note,
    });
    return response.data;
  },

  finalRespondToOffer: async (offerId: string, accepted: boolean): Promise<import('../types/candidateDomain').JobOfferResponse> => {
    const response = await api.put(`/v1/offers/${offerId}/candidate-final-response`, {
      decision: accepted ? 'ACCEPT' : 'REJECT',
    });
    return response.data;
  },
};

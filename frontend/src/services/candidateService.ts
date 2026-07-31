import { api } from './api';
import type {
  CandidateApplication,
  CandidateProfile,
  CvFile,
  CvVersion,
  NotificationItem,
  SubscriptionView,
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

  getCvs: async (): Promise<CvFile[]> => {
    const response = await api.get<CvFile[]>('/candidate/cvs');
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

  getCvVersions: async (): Promise<CvVersion[]> => {
    const response = await api.get<CvVersion[]>('/candidate/cv-versions');
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

  getSavedJobs: async (): Promise<Job[]> => {
    const response = await api.get<Job[]>('/candidate/saved-jobs');
    return response.data;
  },

  saveJob: async (jobId: string): Promise<void> => {
    await api.post(`/candidate/saved-jobs/${jobId}`);
  },

  unsaveJob: async (jobId: string): Promise<void> => {
    await api.delete(`/candidate/saved-jobs/${jobId}`);
  },

  apply: async (jobId: string, cvId?: string, cvVersionId?: string): Promise<CandidateApplication> => {
    const response = await api.post<CandidateApplication>('/applications', { jobId, cvId, cvVersionId });
    return response.data;
  },

  getApplications: async (): Promise<CandidateApplication[]> => {
    const response = await api.get<CandidateApplication[]>('/applications/me');
    return response.data;
  },

  getApplication: async (id: string): Promise<CandidateApplication> => {
    const response = await api.get<CandidateApplication>(`/applications/me/${id}`);
    return response.data;
  },

  getNotifications: async (): Promise<NotificationItem[]> => {
    const response = await api.get<NotificationItem[]>('/candidate/notifications');
    return response.data;
  },

  markNotificationRead: async (id: string): Promise<void> => {
    await api.patch(`/candidate/notifications/${id}/read`);
  },

  markAllNotificationsRead: async (): Promise<void> => {
    await api.patch('/candidate/notifications/read-all');
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

  respondToOffer: async (offerId: string, accepted: boolean, note?: string): Promise<import('../types/candidateDomain').JobOfferResponse> => {
    const response = await api.put(`/v1/offers/${offerId}/response`, null, { params: { accepted, note } });
    return response.data;
  },

  finalRespondToOffer: async (offerId: string, accepted: boolean): Promise<import('../types/candidateDomain').JobOfferResponse> => {
    const response = await api.put(`/v1/offers/${offerId}/candidate-final-response`, null, { params: { accepted } });
    return response.data;
  },
};

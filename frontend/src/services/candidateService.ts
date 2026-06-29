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

  getCvVersions: async (): Promise<CvVersion[]> => {
    const response = await api.get<CvVersion[]>('/candidate/cv-versions');
    return response.data;
  },

  createCvVersion: async (title: string, snapshot: Record<string, unknown>): Promise<CvVersion> => {
    const response = await api.post<CvVersion>('/candidate/cv-versions', { title, templateKey: 'classic', snapshot });
    return response.data;
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

  getSubscription: async (): Promise<SubscriptionView> => {
    const response = await api.get<SubscriptionView>('/candidate/subscription');
    return response.data;
  },
};

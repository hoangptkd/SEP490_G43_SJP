import { api } from './api';
import type { Company, CompanyLocation, CompanyDocument, Job } from '../types/job';
import type { CandidateApplication, NotificationItem } from '../types/candidateDomain';

export interface EmployerDashboardStats {
  totalJobs: number;
  jobGrowthPercentage: number;
  activeJobs: number;
  totalApplications: number;
  pipeline: {
    appliedCount: number;
    reviewedCount: number;
    interviewCount: number;
    offerCount: number;
    hiredCount: number;
    newlyAppliedCount: number;
    shortlistedCount: number;
    interviewScheduledCount: number;
  };
  applicationGrowthPercentage: number;
  pendingApplications: number;
  applicationsByStatus: Record<string, number>;
  recentApplications: CandidateApplication[];
  upcomingInterviews?: {
    id: string;
    candidateName: string;
    jobTitle: string;
    scheduledAt: string;
    type: string;
    status: string;
    meetingLink?: string;
  }[];
  pendingTasks?: {
    id: string;
    title: string;
    description: string;
    taskType: string;
    actionUrl: string;
    createdAt: string;
  }[];
  applicationTrend: { date: string; count: number }[];
}

export interface TaxCodeLookupResult {
  taxCode: string;
  companyName: string;
  address?: string;
  status?: string;
}

export interface EmployerInterview {
  id: string;
  applicationId: string;
  roundNumber: number;
  status: string;
  scheduledAt: string;
  location: string | null;
  meetingLink: string | null;
  viewedAt: string | null;
  candidateRescheduleNote: string | null;
  candidateName: string;
  candidatePhone: string | null;
  candidateEmail: string | null;
  jobTitle: string;
  jobId: string | null;
  createdAt: string;
  updatedAt: string;
}

export const employerService = {
  getCompanyProfile: async (): Promise<Company> => {
    const response = await api.get<Company>('/employer/company');
    return response.data;
  },

  getDashboardStats: async (startDate?: string, endDate?: string): Promise<EmployerDashboardStats> => {
    const params: Record<string, string> = {};
    if (startDate && endDate) {
      params.startDate = startDate;
      params.endDate = endDate;
    }
    const response = await api.get<EmployerDashboardStats>('/employer/dashboard', { params });
    return response.data;
  },

  getInterviews: async (range?: 'today' | 'week' | 'all'): Promise<EmployerInterview[]> => {
    const response = await api.get<EmployerInterview[]>('/employer/interviews', {
      params: range ? { range } : undefined,
    });
    return response.data;
  },

  updateCompanyProfile: async (company: Partial<Company>): Promise<Company> => {
    const response = await api.put<Company>('/employer/company', company);
    return response.data;
  },

  lookupTaxCode: async (taxCode: string, signal?: AbortSignal): Promise<TaxCodeLookupResult> => {
    const response = await api.get<TaxCodeLookupResult>('/employer/company/tax-code-lookup', {
      params: { taxCode },
      signal,
    });
    return response.data;
  },

  updatePersonalProfile: async (profile: { fullName: string; phone: string; position: string }): Promise<{ message: string }> => {
    const response = await api.put<{ message: string }>('/employer/profile/personal', profile);
    return response.data;
  },

  getPersonalProfile: async (): Promise<{ fullName: string; phone: string; position: string }> => {
    const response = await api.get<{ fullName: string; phone: string; position: string }>('/employer/profile/personal');
    return response.data;
  },

  uploadLogo: async (file: File): Promise<Company> => {
    const formData = new FormData();
    formData.append('file', file);
    const response = await api.post<Company>('/employer/company/logo', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data;
  },

  getLocations: async (): Promise<CompanyLocation[]> => {
    const response = await api.get<CompanyLocation[]>('/employer/company/locations');
    return response.data;
  },

  createLocation: async (data: Partial<CompanyLocation>): Promise<CompanyLocation> => {
    const response = await api.post<CompanyLocation>('/employer/company/locations', data);
    return response.data;
  },

  updateLocation: async (id: string, data: Partial<CompanyLocation>): Promise<CompanyLocation> => {
    const response = await api.put<CompanyLocation>(`/employer/company/locations/${id}`, data);
    return response.data;
  },

  deleteLocation: async (id: string): Promise<void> => {
    await api.delete(`/employer/company/locations/${id}`);
  },

  getDocuments: async (): Promise<CompanyDocument[]> => {
    const response = await api.get<CompanyDocument[]>('/employer/company/documents');
    return response.data;
  },

  uploadDocument: async (file: File): Promise<CompanyDocument> => {
    const formData = new FormData();
    formData.append('file', file);
    const response = await api.post<CompanyDocument>('/employer/company/documents', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data;
  },

  replaceDocument: async (id: string, file: File): Promise<CompanyDocument> => {
    const formData = new FormData();
    formData.append('file', file);
    const response = await api.post<CompanyDocument>(`/employer/company/documents/${id}/replace`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data;
  },

  deleteDocument: async (id: string): Promise<void> => {
    await api.delete(`/employer/company/documents/${id}`);
  },

  getJobs: async (params?: { status?: string; search?: string; page?: number; size?: number }): Promise<import('../types/candidateDomain').PageResult<Job>> => {
    const response = await api.get<import('../types/candidateDomain').PageResult<Job>>('/employer/jobs', { params });
    return response.data;
  },

  createJob: async (jobData: Partial<Job>): Promise<Job> => {
    const response = await api.post<Job>('/employer/jobs', jobData);
    return response.data;
  },

  updateJob: async (id: string, jobData: Partial<Job>): Promise<Job> => {
    const response = await api.put<Job>(`/employer/jobs/${id}`, jobData);
    return response.data;
  },

  submitJobForReview: async (id: string): Promise<Job> => {
    const response = await api.post<Job>(`/employer/jobs/${id}/submit-review`);
    return response.data;
  },

  deleteJob: async (id: string): Promise<void> => {
    await api.delete(`/employer/jobs/${id}`);
  },

  closeJob: async (id: string): Promise<Job> => {
    const response = await api.post<Job>(`/employer/jobs/${id}/close`);
    return response.data;
  },

  reopenJob: async (id: string, newDeadline?: string): Promise<Job> => {
    const response = await api.post<Job>(`/employer/jobs/${id}/reopen`, { deadline: newDeadline });
    return response.data;
  },

  getApplications: async (params?: { jobId?: string; status?: string | string[]; search?: string; page?: number; size?: number }): Promise<import('../types/candidateDomain').PageResult<CandidateApplication>> => {
    const response = await api.get<import('../types/candidateDomain').PageResult<CandidateApplication>>('/employer/applications', {
      params,
      paramsSerializer: {
        indexes: null,
      },
    });
    return response.data;
  },

  getJobApplications: async (jobId: string, params?: { status?: string; search?: string; page?: number; size?: number }): Promise<import('../types/candidateDomain').PageResult<CandidateApplication>> => {
    const response = await api.get<import('../types/candidateDomain').PageResult<CandidateApplication>>(`/employer/jobs/${jobId}/applications`, { params });
    return response.data;
  },

  getApplicationDetail: async (id: string): Promise<CandidateApplication> => {
    const response = await api.get<CandidateApplication>(`/employer/applications/${id}`);
    return response.data;
  },

  updateApplicationStatus: async (applicationId: string, status: string, note?: string): Promise<CandidateApplication> => {
    const response = await api.put<CandidateApplication>(`/employer/applications/${applicationId}/status`, { status, note });
    return response.data;
  },

  triggerBulkAiRanking: async (jobId: string): Promise<{ message: string }> => {
    const response = await api.post<{ message: string }>(`/employer/jobs/${jobId}/ai-rank-bulk`);
    return response.data;
  },

  downloadApplicationCv: async (id: string): Promise<Blob> => {
    const response = await api.get<Blob>(`/employer/applications/${id}/cv`, { responseType: 'blob' });
    return response.data;
  },

  getNotifications: async (): Promise<NotificationItem[]> => {
    const response = await api.get<NotificationItem[]>('/employer/notifications');
    return response.data;
  },

  markNotificationRead: async (id: string): Promise<void> => {
    await api.patch(`/employer/notifications/${id}/read`);
  },

  markAllNotificationsRead: async (): Promise<void> => {
    await api.patch('/employer/notifications/read-all');
  },

  scheduleInterview: async (applicationId: string, data: import('../types/candidateDomain').InterviewScheduleRequest): Promise<import('../types/candidateDomain').InterviewScheduleResponse> => {
    const response = await api.post(`/v1/applications/${applicationId}/interviews`, data);
    return response.data;
  },

  updateInterviewResult: async (interviewId: string, data: import('../types/candidateDomain').InterviewResultRequest): Promise<import('../types/candidateDomain').InterviewScheduleResponse> => {
    const response = await api.put(`/v1/interviews/${interviewId}/result`, data);
    return response.data;
  },

  employerRespondToReschedule: async (interviewId: string, responseStatus: string, note?: string, scheduledAt?: string): Promise<import('../types/candidateDomain').InterviewScheduleResponse> => {
    const response = await api.put(`/v1/interviews/${interviewId}/employer-reschedule-response`, { response: responseStatus, note, scheduledAt });
    return response.data;
  },

  employerUpdateInterviewResult: async (interviewId: string, result: 'pass' | 'fail', note?: string): Promise<import('../types/candidateDomain').InterviewScheduleResponse> => {
    const response = await api.put(`/v1/interviews/${interviewId}/result`, { result, note });
    return response.data;
  },

  createJobOffer: async (applicationId: string, data: import('../types/candidateDomain').JobOfferRequest): Promise<import('../types/candidateDomain').JobOfferResponse> => {
    const response = await api.post(`/v1/applications/${applicationId}/offers`, data);
    return response.data;
  },

  employerRespondToOfferRejection: async (offerId: string, isUpdating: boolean, updateData?: import('../types/candidateDomain').JobOfferRequest): Promise<import('../types/candidateDomain').JobOfferResponse> => {
    const response = await api.put(`/v1/offers/${offerId}/employer-response`, updateData, { params: { isUpdating } });
    return response.data;
  },

  rejectApplication: async (applicationId: string, note?: string): Promise<void> => {
    await api.post(`/v1/applications/${applicationId}/reject`, null, { params: { note } });
  },

  getAiRankingQuota: async (): Promise<{ used: number; limit: number; remaining: number; isUnlimited: boolean }> => {
    const response = await api.get<{ used: number; limit: number; remaining: number; isUnlimited: boolean }>('/employer/ai-ranking-quota');
    return response.data;
  },
};

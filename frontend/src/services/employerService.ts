import { api } from './api';
import type { Company, CompanyLocation, CompanyDocument, Job } from '../types/job';

export const employerService = {
  getCompanyProfile: async (): Promise<Company> => {
    const response = await api.get<Company>('/employer/company');
    return response.data;
  },

  updateCompanyProfile: async (company: Partial<Company>): Promise<Company> => {
    const response = await api.put<Company>('/employer/company', company);
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

  deleteDocument: async (id: string): Promise<void> => {
    await api.delete(`/employer/company/documents/${id}`);
  },

  getJobs: async (): Promise<Job[]> => {
    const response = await api.get<Job[]>('/employer/jobs');
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
};

import { api } from './api';
import type { Job, JobFilters, JobApiResponse, Recommendation } from '../types/job';

export const jobService = {
  getAll: async (filters: JobFilters, page = 0, size = 10): Promise<JobApiResponse> => {
    const params = new URLSearchParams({
      page: page.toString(),
      size: size.toString(),
      ...(filters.search && { search: filters.search }),
      ...(filters.location && { location: filters.location }),
      ...(filters.minSalary && { minSalary: filters.minSalary.toString() }),
      ...(filters.maxSalary && { maxSalary: filters.maxSalary.toString() }),
      ...(filters.experienceLevel && { experienceLevel: filters.experienceLevel }),
      ...(filters.skills && { skills: filters.skills }),
      ...(filters.sort && { sort: filters.sort }),
    });
    const response = await api.get<JobApiResponse>(`/jobs?${params}`);
    return response.data;
  },

  getById: async (id: string): Promise<Job> => {
    const response = await api.get<Job>(`/jobs/${id}`);
    return response.data;
  },

  recommendations: async (): Promise<Recommendation[]> => {
    const response = await api.get<Recommendation[]>('/candidate/recommendations/jobs');
    return response.data;
  },

  create: async (jobData: Partial<Job>): Promise<Job> => {
    const response = await api.post<Job>('/jobs', jobData);
    return response.data;
  },

  update: async (id: string, jobData: Partial<Job>): Promise<Job> => {
    const response = await api.put<Job>(`/jobs/${id}`, jobData);
    return response.data;
  },

  delete: async (id: string): Promise<void> => {
    await api.delete(`/jobs/${id}`);
  },
};

import { api } from './api';
import type { Job, JobFilters, JobApiResponse } from '../types/job';

export const jobService = {
  getAll: async (filters: JobFilters, page = 0, size = 10): Promise<JobApiResponse> => {
    const params = new URLSearchParams({
      page: page.toString(),
      size: size.toString(),
      ...(filters.search && { search: filters.search }),
      ...(filters.location && { location: filters.location }),
      ...(filters.minSalary && { minSalary: filters.minSalary.toString() }),
      ...(filters.maxSalary && { maxSalary: filters.maxSalary.toString() }),
    });
    const response = await api.get<JobApiResponse>(`/jobs?${params}`);
    return response.data;
  },

  getById: async (id: number): Promise<Job> => {
    const response = await api.get<Job>(`/jobs/${id}`);
    return response.data;
  },

  create: async (jobData: Partial<Job>): Promise<Job> => {
    const response = await api.post<Job>('/jobs', jobData);
    return response.data;
  },

  update: async (id: number, jobData: Partial<Job>): Promise<Job> => {
    const response = await api.put<Job>(`/jobs/${id}`, jobData);
    return response.data;
  },

  delete: async (id: number): Promise<void> => {
    await api.delete(`/jobs/${id}`);
  },
};
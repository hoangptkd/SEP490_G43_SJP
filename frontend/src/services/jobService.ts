import { api } from './api';
import type { Job, JobFilters, JobApiResponse, Recommendation, Category } from '../types/job';

export const jobService = {
  getCategories: async (): Promise<Category[]> => {
    const response = await api.get<Category[]>('/categories');
    return response.data;
  },

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
      ...(filters.category && { category: filters.category }),
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

};

import { api } from './api';
import type { Job, JobFilters, JobApiResponse, Recommendation, Category, PublicCompany } from '../types/job';

export const jobService = {
  getCategories: async (): Promise<Category[]> => {
    const response = await api.get<Category[]>('/categories');
    return response.data;
  },

  getAll: async (filters: JobFilters, page = 0, size = 10, signal?: AbortSignal): Promise<JobApiResponse> => {
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
      ...(filters.jobType && { jobType: filters.jobType }),
      ...(filters.workMode && { workMode: filters.workMode }),
      ...(filters.sort && { sort: filters.sort }),
    });
    const response = await api.get<JobApiResponse>(`/jobs?${params}`, { signal });
    return response.data;
  },

  getById: async (id: string): Promise<Job> => {
    const response = await api.get<Job>(`/jobs/${id}`);
    return response.data;
  },

  getCompany: async (id: string, page = 0, size = 10): Promise<PublicCompany> => {
    const response = await api.get<PublicCompany>(`/companies/${id}`, { params: { page, size } });
    return response.data;
  },

  recommendations: async (): Promise<Recommendation[]> => {
    const response = await api.get<Recommendation[]>('/candidate/recommendations/jobs');
    return response.data;
  },

};

import { api } from './api';
import type { Company, CompanyLocation } from '../types/job';

export const employerService = {
  getCompanyProfile: async (): Promise<Company> => {
    const response = await api.get<Company>('/employer/company');
    return response.data;
  },

  updateCompanyProfile: async (company: Partial<Company>): Promise<Company> => {
    const response = await api.put<Company>('/employer/company', company);
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
};

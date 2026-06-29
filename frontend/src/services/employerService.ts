import { api } from './api';
import type { Company } from '../types/job';

export const employerService = {
  getCompanyProfile: async (): Promise<Company> => {
    const response = await api.get<Company>('/employer/company');
    return response.data;
  },

  updateCompanyProfile: async (company: Partial<Company>): Promise<Company> => {
    const response = await api.put<Company>('/employer/company', company);
    return response.data;
  },
};

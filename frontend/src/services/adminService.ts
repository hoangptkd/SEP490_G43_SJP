import { api } from './api';
import type { AdminCompanyDetail, AdminCompanySummary, CompanyReviewFilter } from '../types/admin';

export const adminService = {
  listCompanies: async (status: CompanyReviewFilter = 'pending'): Promise<AdminCompanySummary[]> => {
    const response = await api.get<AdminCompanySummary[]>('/admin/companies', { params: { status } });
    return response.data;
  },

  getCompanyDetail: async (id: string): Promise<AdminCompanyDetail> => {
    const response = await api.get<AdminCompanyDetail>(`/admin/companies/${id}`);
    return response.data;
  },

  approveCompany: async (id: string): Promise<AdminCompanyDetail> => {
    const response = await api.post<AdminCompanyDetail>(`/admin/companies/${id}/approve`);
    return response.data;
  },

  rejectCompany: async (id: string, reason: string): Promise<AdminCompanyDetail> => {
    const response = await api.post<AdminCompanyDetail>(`/admin/companies/${id}/reject`, { reason });
    return response.data;
  },
};

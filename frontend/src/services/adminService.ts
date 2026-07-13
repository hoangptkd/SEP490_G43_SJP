import { api } from './api';
import type {
  AdminCompanyDetail,
  AdminCompanySummary,
  AdminDashboardStats,
  AdminJobDetail,
  AdminJobSummary,
  AdminUserRoleFilter,
  AdminUserStatusFilter,
  AdminUserSummary,
  CompanyReviewFilter,
  JobReviewFilter,
} from '../types/admin';

export const adminService = {
  getDashboardStats: async (): Promise<AdminDashboardStats> => {
    const response = await api.get<AdminDashboardStats>('/admin/dashboard');
    return response.data;
  },

  listUsers: async (
    role: AdminUserRoleFilter = 'all',
    status: AdminUserStatusFilter = 'all',
  ): Promise<AdminUserSummary[]> => {
    const response = await api.get<AdminUserSummary[]>('/admin/users', { params: { role, status } });
    return response.data;
  },

  suspendUser: async (id: string): Promise<AdminUserSummary> => {
    const response = await api.post<AdminUserSummary>(`/admin/users/${id}/suspend`);
    return response.data;
  },

  activateUser: async (id: string): Promise<AdminUserSummary> => {
    const response = await api.post<AdminUserSummary>(`/admin/users/${id}/activate`);
    return response.data;
  },

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

  listJobs: async (status: JobReviewFilter = 'pending_review'): Promise<AdminJobSummary[]> => {
    const response = await api.get<AdminJobSummary[]>('/admin/jobs', { params: { status } });
    return response.data;
  },

  getJobDetail: async (id: string): Promise<AdminJobDetail> => {
    const response = await api.get<AdminJobDetail>(`/admin/jobs/${id}`);
    return response.data;
  },

  approveJob: async (id: string): Promise<AdminJobDetail> => {
    const response = await api.post<AdminJobDetail>(`/admin/jobs/${id}/approve`);
    return response.data;
  },

  rejectJob: async (id: string, reason: string): Promise<AdminJobDetail> => {
    const response = await api.post<AdminJobDetail>(`/admin/jobs/${id}/reject`, { reason });
    return response.data;
  },
};

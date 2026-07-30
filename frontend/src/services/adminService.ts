import { api } from './api';
import type {
  AdminAuditLog,
  AdminCategory,
  AdminCompanyDetail,
  AdminCompanySummary,
  AdminDashboardStats,
  AdminJobDetail,
  AdminJobReport,
  AdminJobSummary,
  AdminPayment,
  AdminPlan,
  AdminRevenueSummary,
  AdminSetting,
  AdminStatistics,
  AdminSubscription,
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

  getStatistics: async (
    period: 'all' | 'week' | 'month' | 'year' = 'all',
    year?: number,
    month?: number,
    date?: string,
  ): Promise<AdminStatistics> => {
    const response = await api.get<AdminStatistics>('/admin/dashboard/statistics', {
      params: { period, year, month, date },
    });
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

  approveCompanyDocument: async (companyId: string, documentId: string): Promise<AdminCompanyDetail> => {
    const response = await api.post<AdminCompanyDetail>(
      `/admin/companies/${companyId}/documents/${documentId}/approve`,
    );
    return response.data;
  },

  rejectCompanyDocument: async (
    companyId: string,
    documentId: string,
    reason: string,
  ): Promise<AdminCompanyDetail> => {
    const response = await api.post<AdminCompanyDetail>(
      `/admin/companies/${companyId}/documents/${documentId}/reject`,
      { reason },
    );
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

  closeJob: async (id: string, reason?: string): Promise<AdminJobDetail> => {
    const response = await api.post<AdminJobDetail>(`/admin/jobs/${id}/close`, { reason: reason || '' });
    return response.data;
  },

  reopenJob: async (id: string): Promise<AdminJobDetail> => {
    const response = await api.post<AdminJobDetail>(`/admin/jobs/${id}/reopen`);
    return response.data;
  },

  listJobReports: async (status = 'pending'): Promise<AdminJobReport[]> => {
    const response = await api.get<AdminJobReport[]>('/admin/jobs/reports', { params: { status } });
    return response.data;
  },

  dismissJobReport: async (reportId: string, adminNote?: string): Promise<AdminJobReport> => {
    const response = await api.post<AdminJobReport>(`/admin/jobs/reports/${reportId}/dismiss`, {
      adminNote: adminNote || '',
    });
    return response.data;
  },

  notifyCompanyJobReport: async (reportId: string, adminNote?: string): Promise<AdminJobReport> => {
    const response = await api.post<AdminJobReport>(`/admin/jobs/reports/${reportId}/notify-company`, {
      adminNote: adminNote || '',
    });
    return response.data;
  },

  resolveJobReport: async (reportId: string, adminNote?: string): Promise<AdminJobReport> => {
    const response = await api.post<AdminJobReport>(`/admin/jobs/reports/${reportId}/resolve`, {
      adminNote: adminNote || '',
    });
    return response.data;
  },

  listPlans: async (status = 'all'): Promise<AdminPlan[]> => {
    const response = await api.get<AdminPlan[]>('/admin/billing/plans', { params: { status } });
    return response.data;
  },

  createPlan: async (payload: Partial<AdminPlan>): Promise<AdminPlan> => {
    const response = await api.post<AdminPlan>('/admin/billing/plans', payload);
    return response.data;
  },

  updatePlan: async (id: string, payload: Partial<AdminPlan>): Promise<AdminPlan> => {
    const response = await api.put<AdminPlan>(`/admin/billing/plans/${id}`, payload);
    return response.data;
  },

  deletePlan: async (id: string): Promise<void> => {
    await api.delete(`/admin/billing/plans/${id}`);
  },

  listSubscriptions: async (status = 'all'): Promise<AdminSubscription[]> => {
    const response = await api.get<AdminSubscription[]>('/admin/billing/subscriptions', { params: { status } });
    return response.data;
  },

  cancelSubscription: async (id: string, reason?: string): Promise<AdminSubscription> => {
    const response = await api.post<AdminSubscription>(`/admin/billing/subscriptions/${id}/cancel`, { reason });
    return response.data;
  },

  activateSubscription: async (id: string): Promise<AdminSubscription> => {
    const response = await api.post<AdminSubscription>(`/admin/billing/subscriptions/${id}/activate`);
    return response.data;
  },

  listPayments: async (status = 'all'): Promise<AdminPayment[]> => {
    const response = await api.get<AdminPayment[]>('/admin/billing/payments', { params: { status } });
    return response.data;
  },

  getRevenueSummary: async (): Promise<AdminRevenueSummary> => {
    const response = await api.get<AdminRevenueSummary>('/admin/billing/revenue');
    return response.data;
  },

  listAuditLogs: async (targetType = 'all', limit = 100): Promise<AdminAuditLog[]> => {
    const response = await api.get<AdminAuditLog[]>('/admin/audit-logs', { params: { targetType, limit } });
    return response.data;
  },

  listCategories: async (status = 'all'): Promise<AdminCategory[]> => {
    const response = await api.get<AdminCategory[]>('/admin/categories', { params: { status } });
    return response.data;
  },

  createCategory: async (payload: { name: string; slug?: string; description?: string; status?: string }): Promise<AdminCategory> => {
    const response = await api.post<AdminCategory>('/admin/categories', payload);
    return response.data;
  },

  updateCategory: async (id: string, payload: { name?: string; slug?: string; description?: string; status?: string }): Promise<AdminCategory> => {
    const response = await api.put<AdminCategory>(`/admin/categories/${id}`, payload);
    return response.data;
  },

  listSettings: async (): Promise<AdminSetting[]> => {
    const response = await api.get<AdminSetting[]>('/admin/settings');
    return response.data;
  },

  updateSettings: async (settings: Record<string, string>): Promise<AdminSetting[]> => {
    const response = await api.put<AdminSetting[]>('/admin/settings', { settings });
    return response.data;
  },
};

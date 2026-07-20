import type { Company, CompanyDocument, Job } from './job';

export interface AdminCompanySummary {
  id: string;
  name: string;
  industry?: string;
  taxCode?: string;
  verificationStatus: string;
  status: string;
  ownerEmail?: string;
  ownerName?: string;
  documentCount: number;
  pendingDocumentCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface AdminCompanyOwner {
  employerId: string;
  email: string;
  fullName?: string;
  phone?: string;
  position?: string;
  verificationStatus: string;
}

export interface AdminCompanyDetail {
  company: Company;
  documents: CompanyDocument[];
  owner?: AdminCompanyOwner | null;
  createdAt: string;
  updatedAt: string;
}

export type CompanyReviewFilter = 'pending' | 'verified' | 'rejected';

export type JobReviewFilter = 'pending_review' | 'published' | 'rejected';

export interface AdminJobSummary {
  id: string;
  title: string;
  companyName?: string;
  employerEmail?: string;
  employerName?: string;
  status: string;
  location?: string;
  salaryMin?: number;
  salaryMax?: number;
  createdAt: string;
  updatedAt: string;
}

export interface AdminJobDetail {
  job: Job;
  employerEmail?: string;
  employerName?: string;
  employerPosition?: string;
  createdAt: string;
  updatedAt: string;
}

export interface AdminDashboardStats {
  totalUsers: number;
  activeJobs: number;
  pendingModeration: number;
  applicationsToday: number;
  pendingCompanies: number;
  pendingJobs: number;
  verifiedCompanies: number;
  totalCompanies: number;
  totalApplications: number;
  totalEmployers: number;
  totalCandidates: number;
  updatedAt: string;
}

export type AdminUserRoleFilter = 'all' | 'candidate' | 'employer' | 'admin';
export type AdminUserStatusFilter = 'all' | 'active' | 'suspended' | 'inactive';

export interface AdminUserSummary {
  id: string;
  email: string;
  fullName?: string;
  phone?: string;
  role: 'CANDIDATE' | 'EMPLOYER' | 'ADMIN' | string;
  status: 'ACTIVE' | 'SUSPENDED' | 'PENDING_VERIFICATION' | string;
  emailVerified: boolean;
  lastLoginAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface AdminStatItem {
  key: string;
  label: string;
  value: number;
}

export interface AdminTrendPoint {
  date: string;
  value: number;
}

export interface AdminStatistics {
  totalUsers: number;
  totalCompanies: number;
  totalJobs: number;
  totalApplications: number;
  totalViews: number;
  usersByRole: AdminStatItem[];
  usersByStatus: AdminStatItem[];
  companiesByVerification: AdminStatItem[];
  jobsByStatus: AdminStatItem[];
  applicationsByStatus: AdminStatItem[];
  usersTrend: AdminTrendPoint[];
  candidateUsersTrend: AdminTrendPoint[];
  employerUsersTrend: AdminTrendPoint[];
  applicationsLast7Days: AdminTrendPoint[];
  jobsLast7Days: AdminTrendPoint[];
  updatedAt: string;
}

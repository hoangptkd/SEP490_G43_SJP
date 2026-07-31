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

export type JobReviewFilter = 'pending_review' | 'published' | 'rejected' | 'closed' | 'removed' | 'reports';

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

export interface AdminJobReport {
  id: string;
  jobId: string;
  jobTitle: string;
  companyName: string;
  jobStatus: string;
  reporterUserId: string;
  reporterEmail: string;
  reporterName?: string;
  reporterPhone?: string;
  reporterDateOfBirth?: string;
  reporterAge?: number;
  reason: string;
  description?: string;
  status: string;
  adminNote?: string;
  createdAt: string;
  resolvedAt?: string;
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
  revenueToday?: number;
  revenueMonth?: number;
  paidCountMonth?: number;
  activeSubscriptions?: number;
  interviewsToday?: number;
  interviewsWeek?: number;
  interviewsCompletedWeek?: number;
  closedJobs?: number;
  applicationsLast7Days?: AdminTrendPoint[];
  revenueLast7Days?: AdminTrendPoint[];
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
  interviewSessions?: number;
  interviewCompleted?: number;
  interviewInProgress?: number;
  aiAnswersEvaluated?: number;
  aiRecommendations?: number;
  aiRankingJobs?: number;
  averageInterviewScore?: number;
  interviewsByStatus?: AdminStatItem[];
  interviewsTrend?: AdminTrendPoint[];
  updatedAt: string;
}

export interface AdminPlan {
  id: string;
  name: string;
  targetRole: string;
  description?: string;
  price: number;
  currency: string;
  durationDays: number;
  featuresJson?: string;
  status: string;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface AdminSubscription {
  id: string;
  userId: string;
  userEmail: string;
  userName?: string;
  planId: string;
  planName: string;
  status: string;
  startDate?: string;
  endDate?: string;
  cancelledAt?: string;
  cancelledReason?: string;
  createdAt: string;
  updatedAt: string;
}

export interface AdminPayment {
  id: string;
  subscriptionId?: string;
  userId: string;
  userEmail: string;
  planName?: string;
  amount: number;
  currency: string;
  paymentMethod?: string;
  gateway?: string;
  status: string;
  transactionId?: string;
  failureReason?: string;
  transferContent?: string;
  qrUrl?: string;
  paidAt?: string;
  createdAt: string;
  expiresAt?: string;
}

export interface AdminRevenueSummary {
  totalPaid: number;
  totalPending: number;
  totalRefunded: number;
  paidCount: number;
  pendingCount: number;
  failedCount: number;
  activeSubscriptions: number;
  activePlans: number;
  updatedAt: string;
}

export interface AdminAuditLog {
  id: string;
  actorUserId?: string;
  actorEmail?: string;
  action: string;
  targetType: string;
  targetId?: string;
  oldValueJson?: string;
  newValueJson?: string;
  ipAddress?: string;
  createdAt: string;
}

export interface AdminCategory {
  id: string;
  name: string;
  slug: string;
  parentId?: string;
  description?: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface AdminSetting {
  key: string;
  value: string;
  description?: string;
  updatedAt: string;
}

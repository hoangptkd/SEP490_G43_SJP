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

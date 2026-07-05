import type { Company, CompanyDocument } from './job';

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

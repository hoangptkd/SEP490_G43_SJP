export interface CompanyLocation {
  id: string;
  branchName: string;
  address?: string;
  city?: string;
  district?: string;
  country?: string;
  headquarter: boolean;
}

export interface Job {
  id: string;
  title: string;
  description: string;
  salaryMin?: number;
  salaryMax?: number;
  location: string;
  requirements: string[];
  skills: string[];
  company: Company;
  companyLocationId?: string;
  companyLocation?: CompanyLocation;
  experienceLevel?: string;
  deadline?: string;
  status: 'ACTIVE' | 'CLOSED' | 'DRAFT' | 'EXPIRED' | 'ARCHIVED';
  saved: boolean;
  applied: boolean;
  matchScore?: number;
}

export interface Company {
  id: string;
  name: string;
  description?: string;
  website?: string;
  industry?: string;
  location?: string;
  companySize?: number;
  taxCode?: string;
  verified?: boolean;
  verificationStatus?: string;
  status?: string;
  locations?: CompanyLocation[];
}

export interface CompanyDocument {
  id: string;
  fileName: string;
  fileUrl: string;
  fileType: string;
  status: string;
  rejectReason?: string;
  uploadedAt: string;
  reviewedAt?: string;
}

export interface JobFilters {
  search?: string;
  location?: string;
  minSalary?: number;
  maxSalary?: number;
  experienceLevel?: string;
  skills?: string;
  sort?: string;
}

export interface JobApiResponse {
  content: Job[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Recommendation {
  job: Job;
  matchScore: number;
  matchedSkills: string[];
  missingSkills: string[];
  reason: string;
  lowConfidence: boolean;
}

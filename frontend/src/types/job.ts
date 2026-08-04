export interface CompanyLocation {
  id: string;
  branchName: string;
  address?: string;
  city?: string;
  district?: string;
  country?: string;
  headquarter: boolean;
}

export interface Category {
  id: string;
  name: string;
  slug: string;
  parentId?: string | null;
  description?: string;
  status?: string;
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
  benefits?: string;
  vacancies?: number;
  workingTime?: string;
  salaryType?: 'range' | 'fixed' | 'negotiable' | string;
  jobType?: 'full_time' | 'part_time' | 'contract' | 'internship' | 'freelance' | string;
  workMode?: 'onsite' | 'remote' | 'hybrid' | string;
  viewsCount?: number;
  company: Company;
  companyLocationId?: string;
  companyLocation?: CompanyLocation;
  experienceLevel?: string;
  deadline?: string;
  status: 'ACTIVE' | 'PUBLISHED' | 'PENDING_REVIEW' | 'REJECTED' | 'CLOSED' | 'DRAFT' | 'EXPIRED' | 'ARCHIVED' | string;
  rejectionReason?: string;
  reportFixDeadline?: string;
  saved: boolean;
  applied: boolean;
  matchScore?: number;
  applicationsCount?: number;
  rankingConfig?: {
    template?: string;
    weights?: Record<string, number>;
    enabled_criteria?: string[];
    mandatory?: {
      skills?: string[];
      certificates?: string[];
      min_experience_years?: number | null;
      education_level?: string | null;
    };
  };
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
  logoUrl?: string;
  verified?: boolean;
  verificationStatus?: string;
  status?: string;
  locations?: CompanyLocation[];
  industries?: CompanyIndustry[];
  submitForReview?: boolean;
}

export interface CompanyIndustry {
  id?: string;
  categoryId: string;
  categoryName?: string;
  categorySlug?: string;
  primary: boolean;
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
  category?: string;
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

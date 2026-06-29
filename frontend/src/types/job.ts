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
  website?: string;
  location?: string;
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

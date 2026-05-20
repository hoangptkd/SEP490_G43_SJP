export interface Job {
  id: number;
  title: string;
  description: string;
  salaryMin?: number;
  salaryMax?: number;
  location: string;
  requirements: string[];
  employer: Employer;
  status: 'ACTIVE' | 'CLOSED' | 'DRAFT';
  createdAt: string;
  updatedAt: string;
}

export interface Employer {
  id: number;
  name: string;
  email: string;
  company: string;
  website?: string;
}

export interface JobFilters {
  search?: string;
  location?: string;
  minSalary?: number;
  maxSalary?: number;
  status?: string;
}

export interface JobApiResponse {
  content: Job[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
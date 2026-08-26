import type { AiJobSearchInput } from '../types/aiJobSearch';
import type { JobFilters } from '../types/job';

export function aiJobSearchInput(cvId: string, filters: JobFilters): AiJobSearchInput {
  return {
    cvId,
    filters: {
      location: filters.location?.trim() || undefined,
      minSalary: filters.minSalary,
      maxSalary: filters.maxSalary,
      jobType: filters.jobType || undefined,
      workMode: filters.workMode || undefined,
    },
  };
}

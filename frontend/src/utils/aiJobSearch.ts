import type { AiJobSearchItem } from '../types/aiJobSearch';
import type { JobFilters } from '../types/job';

export function filterAiJobSearchItems(items: AiJobSearchItem[], filters: JobFilters) {
  return items.filter((item) => {
    const job = item.job;
    if (filters.location && !job.location?.toLowerCase().includes(filters.location.toLowerCase())) return false;
    if (filters.minSalary !== undefined && job.salaryMax != null && job.salaryMax < filters.minSalary) return false;
    if (filters.maxSalary !== undefined && job.salaryMin != null && job.salaryMin > filters.maxSalary) return false;
    if (filters.jobType && job.jobType !== filters.jobType) return false;
    if (filters.workMode && job.workMode !== filters.workMode) return false;
    return true;
  });
}

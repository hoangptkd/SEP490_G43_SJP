import type { Job } from './job';

export interface AiJobSearchFilters {
  location?: string;
  minSalary?: number;
  maxSalary?: number;
  jobType?: string;
  workMode?: string;
}

export interface AiJobSearchInput {
  cvId: string;
  filters: AiJobSearchFilters;
}

export interface AiJobSearchQuota {
  used: number;
  limit: number;
  remaining: number;
  resetAt: string;
}

export interface AiJobSearchStatus {
  enabled: boolean;
  consentRequired: boolean;
  policyVersion: string;
  readiness: {
    profileAvailable: boolean;
    cvAvailable: boolean;
    lowConfidence: boolean;
    missingItems: string[];
  };
  quota: AiJobSearchQuota;
  cache: {
    available: boolean;
    generatedAt?: string | null;
    expiresAt?: string | null;
    stale: boolean;
  };
}

export interface AiJobSearchItem {
  rank: number;
  job: Job;
  matchScore: number;
  matchedSkills: string[];
  missingSkills: string[];
  reason: string;
  evidence: { cvQuote: string; jobQuote: string }[];
}

export interface AiJobSearchResult {
  source: 'AI' | 'PROFILE_FALLBACK';
  cvId: string;
  runId?: string | null;
  cached: boolean;
  stale: boolean;
  lowConfidence: boolean;
  generatedAt?: string | null;
  expiresAt?: string | null;
  quota: AiJobSearchQuota;
  items: AiJobSearchItem[];
}

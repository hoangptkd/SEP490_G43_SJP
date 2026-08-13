import type { Job } from './job';

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
    defaultCvAvailable: boolean;
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
}

export interface AiJobSearchResult {
  source: 'AI';
  runId?: string | null;
  cached: boolean;
  stale: boolean;
  lowConfidence: boolean;
  generatedAt?: string | null;
  expiresAt?: string | null;
  quota: AiJobSearchQuota;
  items: AiJobSearchItem[];
}

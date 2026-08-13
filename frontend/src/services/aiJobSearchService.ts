import { api } from './api';
import type { AiJobSearchItem, AiJobSearchResult, AiJobSearchStatus } from '../types/aiJobSearch';

export const aiJobSearchService = {
  status: async (signal?: AbortSignal): Promise<AiJobSearchStatus> => {
    const response = await api.get<AiJobSearchStatus>('/candidate/ai-job-search/status', { signal });
    return response.data;
  },

  consent: async (policyVersion: string): Promise<AiJobSearchStatus> => {
    const response = await api.post<AiJobSearchStatus>('/candidate/ai-job-search/consent', {
      accepted: true,
      policyVersion,
    });
    return response.data;
  },

  revokeConsent: async (): Promise<void> => {
    await api.delete('/candidate/ai-job-search/consent');
  },

  search: async (forceRefresh = false): Promise<AiJobSearchResult> => {
    const response = await api.post<AiJobSearchResult>('/candidate/ai-job-search/search', { forceRefresh });
    return response.data;
  },

  recommendation: async (runId: string, jobId: string): Promise<AiJobSearchItem> => {
    const response = await api.get<AiJobSearchItem>(
      `/candidate/ai-job-search/runs/${runId}/jobs/${jobId}`,
    );
    return response.data;
  },
};

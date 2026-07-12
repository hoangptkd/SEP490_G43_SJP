import { api } from './api';
import type {
  AiInterviewConfig,
  AiInterviewEligibleApplication,
  AiInterviewSession,
  AiInterviewTranscript,
} from '../types/aiInterview';

export const aiInterviewService = {
  configStatus: async (): Promise<AiInterviewConfig> => {
    const response = await api.get<AiInterviewConfig>('/candidate/ai-interviews/config-status');
    return response.data;
  },

  eligibleApplications: async (): Promise<AiInterviewEligibleApplication[]> => {
    const response = await api.get<AiInterviewEligibleApplication[]>('/candidate/ai-interviews/eligible-applications');
    return response.data;
  },

  sessions: async (): Promise<AiInterviewSession[]> => {
    const response = await api.get<AiInterviewSession[]>('/candidate/ai-interviews/sessions');
    return response.data;
  },

  getSession: async (id: string): Promise<AiInterviewSession> => {
    const response = await api.get<AiInterviewSession>(`/candidate/ai-interviews/sessions/${id}`);
    return response.data;
  },

  createApplicationSession: async (applicationId: string): Promise<AiInterviewSession> => {
    const response = await api.post<AiInterviewSession>('/candidate/ai-interviews/sessions/application', { applicationId });
    return response.data;
  },

  createPracticeSession: async (targetRole: string, skills: string[], jobId?: string): Promise<AiInterviewSession> => {
    const response = await api.post<AiInterviewSession>('/candidate/ai-interviews/sessions/practice', {
      targetRole,
      skills,
      jobId: jobId || null,
    });
    return response.data;
  },

  deleteSession: async (id: string): Promise<void> => {
    await api.delete(`/candidate/ai-interviews/sessions/${id}`);
  },

  uploadAudio: async (sessionId: string, file: File, durationSeconds: number): Promise<AiInterviewTranscript> => {
    const form = new FormData();
    form.append('file', file);
    form.append('durationSeconds', String(Math.max(1, Math.ceil(durationSeconds))));
    const response = await api.post<AiInterviewTranscript>(
      `/candidate/ai-interviews/sessions/${sessionId}/questions/current/audio`,
      form,
      { headers: { 'Content-Type': 'multipart/form-data' } },
    );
    return response.data;
  },

  submitAnswer: async (sessionId: string, questionId: string, transcript: string): Promise<AiInterviewSession> => {
    const response = await api.post<AiInterviewSession>(
      `/candidate/ai-interviews/sessions/${sessionId}/questions/${questionId}/answer`,
      { transcript },
    );
    return response.data;
  },

  skipQuestion: async (sessionId: string, questionId: string): Promise<AiInterviewSession> => {
    const response = await api.post<AiInterviewSession>(
      `/candidate/ai-interviews/sessions/${sessionId}/questions/${questionId}/skip`,
    );
    return response.data;
  },

  retryFeedback: async (sessionId: string, questionId: string): Promise<AiInterviewSession> => {
    const response = await api.post<AiInterviewSession>(
      `/candidate/ai-interviews/sessions/${sessionId}/questions/${questionId}/feedback/retry`,
    );
    return response.data;
  },

  retrySummary: async (sessionId: string): Promise<AiInterviewSession> => {
    const response = await api.post<AiInterviewSession>(
      `/candidate/ai-interviews/sessions/${sessionId}/summary/retry`,
    );
    return response.data;
  },
};

import { api } from './api';
import type {
  AiInterviewConfig,
  AiInterviewEligibleApplication,
  AiInterviewQuestionSet,
  AiInterviewSession,
  AiInterviewSpeechTicket,
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

  questionSets: async (): Promise<AiInterviewQuestionSet[]> => {
    const response = await api.get<AiInterviewQuestionSet[]>('/candidate/ai-interviews/question-sets');
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

  createPracticeSession: async (
    targetRole: string,
    skills: string[],
    jobId?: string,
    questionSetId?: string,
  ): Promise<AiInterviewSession> => {
    const response = await api.post<AiInterviewSession>('/candidate/ai-interviews/sessions/practice', {
      targetRole,
      skills,
      jobId: jobId || null,
      questionSetId: questionSetId || null,
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

  createSpeechTicket: async (sessionId: string, input: string): Promise<AiInterviewSpeechTicket> => {
    const response = await api.post<AiInterviewSpeechTicket>(
      `/candidate/ai-interviews/sessions/${sessionId}/speech`,
      { input },
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

  confirmAnswer: async (sessionId: string, questionId: string, transcript: string): Promise<AiInterviewSession> => {
    const response = await api.post<AiInterviewSession>(
      `/candidate/ai-interviews/sessions/${sessionId}/questions/${questionId}/confirm`,
      { transcript },
    );
    return response.data;
  },

  finishInterview: async (
    sessionId: string,
    currentAnswer?: { questionId: string; transcript: string },
  ): Promise<AiInterviewSession> => {
    const response = await api.post<AiInterviewSession>(
      `/candidate/ai-interviews/sessions/${sessionId}/finish`,
      currentAnswer || {},
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

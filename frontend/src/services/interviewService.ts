import { api } from '../api';
import type { Interview, InterviewResponse, InterviewFeedback, InterviewQuestion } from '../../types/interview';

export interface CreateInterviewRequest {
  applicationId: number;
  type: 'AI_INTERVIEW' | 'HUMAN_INTERVIEW';
  scheduledAt: string;
  durationMinutes: number;
}

export interface SubmitInterviewResponseRequest {
  questionId: number;
  answerText?: string;
  videoUrl?: string;
}

export const interviewService = {
  getAllByApplication: async (applicationId: number): Promise<Interview[]> => {
    const response = await api.get<Interview[]>(`/interviews/application/${applicationId}`);
    return response.data;
  },

  getById: async (id: number): Promise<Interview> => {
    const response = await api.get<Interview>(`/interviews/${id}`);
    return response.data;
  },

  create: async (data: CreateInterviewRequest): Promise<Interview> => {
    const response = await api.post<Interview>('/interviews', data);
    return response.data;
  },

  submitResponse: async (id: number, data: SubmitInterviewResponseRequest): Promise<InterviewResponse> => {
    const response = await api.post<InterviewResponse>(`/interviews/${id}/responses`, data);
    return response.data;
  },

  getFeedback: async (id: number): Promise<InterviewFeedback> => {
    const response = await api.get<InterviewFeedback>(`/interviews/${id}/feedback`);
    return response.data;
  },

  getQuestions: async (id: number): Promise<InterviewQuestion[]> => {
    const response = await api.get<InterviewQuestion[]>(`/interviews/${id}/questions`);
    return response.data;
  },
};
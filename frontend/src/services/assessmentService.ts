import { api } from './api';
import type { Assessment, AssessmentResult } from '../types/assessment';

export interface CreateAssessmentRequest {
  jobId?: number;
  title: string;
  type: 'QUIZ' | 'CODING' | 'MULTIPLE_CHOICE' | 'VIDEO';
  questions: object[];
  durationMinutes: number;
}

export interface SubmitAssessmentRequest {
  answers: { questionId: number; answer: string | number }[];
}

export const assessmentService = {
  getByJob: async (jobId: number): Promise<Assessment[]> => {
    const response = await api.get<Assessment[]>(`/assessments/job/${jobId}`);
    return response.data;
  },

  getByCandidate: async (candidateId: number): Promise<Assessment[]> => {
    const response = await api.get<Assessment[]>(`/assessments/candidate/${candidateId}`);
    return response.data;
  },

  getById: async (id: number): Promise<Assessment> => {
    const response = await api.get<Assessment>(`/assessments/${id}`);
    return response.data;
  },

  create: async (data: CreateAssessmentRequest): Promise<Assessment> => {
    const response = await api.post<Assessment>('/assessments', data);
    return response.data;
  },

  submit: async (id: number, data: SubmitAssessmentRequest): Promise<AssessmentResult> => {
    const response = await api.post<AssessmentResult>(`/assessments/${id}/submit`, data);
    return response.data;
  },
};

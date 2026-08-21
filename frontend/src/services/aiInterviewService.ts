import { api } from './api';
import type {
  AiInterviewConfig,
  AiInterviewConfirmedAnswer,
  AiInterviewCvProfile,
  AiInterviewEligibleApplication,
  AiInterviewPracticeInput,
  AiInterviewQuestionSet,
  AiInterviewSession,
  AiInterviewSpeechTicket,
  AiInterviewTranscript,
  AiInterviewTranscriptionTicket,
  HandsFreeAnswerCaptureResult,
  HandsFreeAudioSegmentUpload,
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

  analyzePracticeCv: async (cvId: string): Promise<AiInterviewCvProfile> => {
    const response = await api.post<AiInterviewCvProfile>('/candidate/ai-interviews/practice/cv-profile', { cvId });
    return response.data;
  },

  createTranscriptionTicket: async (sessionId: string): Promise<AiInterviewTranscriptionTicket> => {
    const response = await api.post<AiInterviewTranscriptionTicket>(
      `/candidate/ai-interviews/sessions/${sessionId}/transcription-ticket`,
    );
    return response.data;
  },

  createPracticeSession: async (input: AiInterviewPracticeInput): Promise<AiInterviewSession> => {
    const response = await api.post<AiInterviewSession>('/candidate/ai-interviews/sessions/practice', input);
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

  finalizeHandsFreeCapture: async (
    sessionId: string,
    questionId: string,
    captureId: string,
    captureVersion: number,
    segments: HandsFreeAudioSegmentUpload[],
    browserTranscript: string,
    transcriptionProvider: 'web_speech' | 'speechmatics_realtime' = 'web_speech',
  ): Promise<HandsFreeAnswerCaptureResult> => {
    const form = new FormData();
    form.append('captureId', captureId);
    form.append('captureVersion', String(captureVersion));
    form.append('browserTranscript', browserTranscript);
    form.append('transcriptionProvider', transcriptionProvider);
    segments.forEach((segment) => {
      form.append('audioSegments', segment.file);
      form.append('segmentSequences', String(segment.sequence));
      form.append('durationSeconds', String(segment.durationSeconds));
    });
    const response = await api.post<HandsFreeAnswerCaptureResult>(
      `/candidate/ai-interviews/sessions/${sessionId}/questions/${questionId}/answer-capture`,
      form,
      {
        headers: {
          'Content-Type': 'multipart/form-data',
          'Idempotency-Key': captureId,
        },
      },
    );
    return response.data;
  },

  finalizeHandsFreeTurnCapture: async (
    sessionId: string,
    turnId: string,
    captureId: string,
    captureVersion: number,
    segments: HandsFreeAudioSegmentUpload[],
    browserTranscript: string,
    transcriptionProvider: 'web_speech' | 'speechmatics_realtime' = 'web_speech',
  ): Promise<HandsFreeAnswerCaptureResult> => {
    const form = new FormData();
    form.append('captureId', captureId);
    form.append('captureVersion', String(captureVersion));
    form.append('browserTranscript', browserTranscript);
    form.append('transcriptionProvider', transcriptionProvider);
    segments.forEach((segment) => {
      form.append('audioSegments', segment.file);
      form.append('segmentSequences', String(segment.sequence));
      form.append('durationSeconds', String(segment.durationSeconds));
    });
    const response = await api.post<HandsFreeAnswerCaptureResult>(
      `/candidate/ai-interviews/sessions/${sessionId}/turns/${turnId}/answer-capture`,
      form,
      {
        headers: {
          'Content-Type': 'multipart/form-data',
          'Idempotency-Key': captureId,
        },
      },
    );
    return { ...response.data, questionId: turnId };
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

  confirmAnswer: async (
    sessionId: string,
    questionId: string,
    answer: AiInterviewConfirmedAnswer,
  ): Promise<AiInterviewSession> => {
    const response = await api.post<AiInterviewSession>(
      `/candidate/ai-interviews/sessions/${sessionId}/questions/${questionId}/confirm`,
      answer,
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

  confirmTurn: async (
    sessionId: string,
    turnId: string,
    idempotencyKey: string,
    answer: AiInterviewConfirmedAnswer,
    expectedDialogueVersion: number,
  ): Promise<AiInterviewSession> => {
    const response = await api.post<AiInterviewSession>(
      `/candidate/ai-interviews/sessions/${sessionId}/turns/${turnId}/confirm`,
      { ...answer, expectedDialogueVersion },
      { headers: { 'Idempotency-Key': idempotencyKey } },
    );
    return response.data;
  },

  skipTurn: async (
    sessionId: string,
    turnId: string,
    idempotencyKey: string,
    expectedDialogueVersion: number,
  ): Promise<AiInterviewSession> => {
    const response = await api.post<AiInterviewSession>(
      `/candidate/ai-interviews/sessions/${sessionId}/turns/${turnId}/skip`,
      { expectedDialogueVersion },
      { headers: { 'Idempotency-Key': idempotencyKey } },
    );
    return response.data;
  },

  replayTurn: async (
    sessionId: string,
    turnId: string,
    expectedDialogueVersion: number,
  ): Promise<AiInterviewSession> => {
    const response = await api.post<AiInterviewSession>(
      `/candidate/ai-interviews/sessions/${sessionId}/turns/${turnId}/replay`,
      { expectedDialogueVersion },
    );
    return response.data;
  },

  retryConversation: async (sessionId: string): Promise<AiInterviewSession> => {
    const response = await api.post<AiInterviewSession>(
      `/candidate/ai-interviews/sessions/${sessionId}/conversation/retry`,
    );
    return response.data;
  },

  recordQuestionReplay: async (sessionId: string, questionId: string): Promise<AiInterviewSession> => {
    const response = await api.post<AiInterviewSession>(
      `/candidate/ai-interviews/sessions/${sessionId}/questions/${questionId}/replay`,
    );
    return response.data;
  },

  retryQuestionGeneration: async (sessionId: string): Promise<AiInterviewSession> => {
    const response = await api.post<AiInterviewSession>(
      `/candidate/ai-interviews/sessions/${sessionId}/questions/retry`,
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

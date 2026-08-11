import type { Job } from './job';

export interface AiInterviewConfig {
  enabled: boolean;
  message?: string;
  questionCount: number;
  audioMaxSeconds: number;
  audioMaxSizeMb: number;
  voiceStreamingEnabled: boolean;
  voiceProvider: string;
  voiceSilenceMs: number;
  voiceConfirmationSilenceMs: number;
  voiceUnclearConfirmationDelayMs: number;
}

export interface AiInterviewEligibleApplication {
  id: string;
  status: string;
  submittedAt?: string;
  job: Job;
}

export interface AiInterviewQuestionSet {
  id: string;
  code: string;
  title: string;
  description?: string;
  targetRole?: string;
  questionCount: number;
}

export interface AiInterviewFeedback {
  id: string;
  score: number;
  feedback: string;
  strengths: string[];
  weaknesses: string[];
  suggestions: string[];
  source: 'provider' | 'fallback' | 'skipped';
  fallback: boolean;
}

export interface AiInterviewAnswer {
  id: string;
  questionId: string;
  transcript?: string;
  skipped: boolean;
  transcriptStatus: string;
  feedbackStatus: string;
  errorMessage?: string;
  answeredAt?: string;
  feedback?: AiInterviewFeedback;
}

export interface AiInterviewQuestion {
  id: string;
  orderIndex: number;
  questionType: string;
  content: string;
  difficulty?: string;
  skillTag?: string;
  timeLimitSeconds?: number;
  answer?: AiInterviewAnswer;
}

export interface AiInterviewSummary {
  overallScore: number;
  summary: string;
  strengths: string[];
  weaknesses: string[];
  improvementPlan: string[];
  source: 'provider' | 'fallback';
  fallback: boolean;
}

export interface AiInterviewSession {
  id: string;
  title: string;
  contextType: 'application' | 'practice';
  status: 'created' | 'in_progress' | 'completed' | 'cancelled';
  totalQuestions: number;
  overallScore?: number;
  applicationId?: string;
  job?: Job;
  practiceContext: Record<string, unknown>;
  startedAt?: string;
  completedAt?: string;
  createdAt?: string;
  updatedAt?: string;
  questions: AiInterviewQuestion[];
  summary?: AiInterviewSummary;
}

export interface AiInterviewTranscript {
  questionId: string;
  transcript: string;
  transcriptStatus: string;
}

export interface HandsFreeVadMetrics {
  speechSegments: Array<{ startMs: number; endMs: number }>;
  speakingDurationSeconds: number;
  speechOnsetSeconds?: number;
  internalPauseCount: number;
  longestInternalPauseSeconds: number;
  totalInternalPauseDurationSeconds: number;
  pauseDurationRatio: number;
  modelVersion: string;
  status: string;
}

export interface HandsFreeAnswerCaptureResult {
  questionId: string;
  captureId: string;
  captureVersion: number;
  browserTranscript: string;
  gladiaTranscript?: string;
  finalTranscript: string;
  transcriptStatus: 'standardized' | 'fallback_browser';
  dataQuality: string;
  vadMetrics?: HandsFreeVadMetrics;
}

export interface HandsFreeAudioSegmentUpload {
  sequence: number;
  file: File;
  durationSeconds: number;
}

export interface AiInterviewSpeechTicket {
  streamUrl: string;
  contentType: string;
  expiresAt: number;
}

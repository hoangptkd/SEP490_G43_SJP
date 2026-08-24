import type { Job } from './job';

export interface AiInterviewConfig {
  enabled: boolean;
  message?: string;
  questionCount: number;
  audioMaxSeconds: number;
  audioMaxSizeMb: number;
  answerTranscriptionProvider: 'web_speech' | 'speechmatics_realtime';
  speechmaticsRealtimeEnabled: boolean;
  voiceStreamingEnabled: boolean;
  voiceProvider: string;
  voiceConfirmationPromptDelayMs: number;
  voiceConfirmationAutoFinalizeMs: number;
  voiceRecognitionRestartDelayMs: number;
  voiceLoadWaitMs: number;
  voiceNextQuestionDelayMs: number;
}

export interface AiInterviewTranscriptionTicket {
  provider: 'speechmatics_realtime';
  websocketPath: string;
  expiresAt: number;
  finalFlushTimeoutMs: number;
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
  questionScore: number;
  evaluationStatus: 'RATED' | 'NOT_ANSWERED';
  barsLevel?: number;
  scoreReason?: 'SKIPPED' | 'NOT_ANSWERED';
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
  rawTranscript?: string;
  finalTranscript?: string;
  transcriptEdited: boolean;
  transcriptEditCount: number;
  conversationState?: InterviewConversationState;
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
  replayCount: number;
  sourceType: 'AI_GENERATED' | 'QUESTION_BANK';
  sourceId?: string;
  promptVersion?: string;
  rubricVersion?: string;
  competencyId?: string;
  answer?: AiInterviewAnswer;
}

export type InterviewConversationState =
  | 'AI_SPEAKING'
  | 'LISTENING'
  | 'WAITING_FOR_CONTINUATION'
  | 'PROCESSING_AUDIO'
  | 'REVIEWING_TRANSCRIPT'
  | 'ANSWER_CONFIRMED'
  | 'NEXT_QUESTION';

export interface AiInterviewCvProfile {
  id: string;
  cvId: string;
  cvTitle: string;
  contentHash: string;
  summary: string;
  experienceLevel: 'intern' | 'fresher' | 'junior' | 'middle' | 'senior';
  skills: string[];
  suggestedRoles: Array<{ title: string; reason: string }>;
  evidenceClaims: Array<{ id: string; topic: string; claim: string }>;
  promptVersion: string;
  cached: boolean;
}

export interface AiInterviewPracticeInput {
  cvId: string;
  targetRole: string;
  seniority: AiInterviewCvProfile['experienceLevel'];
  focusSkills: string[];
}

export interface AiInterviewSummary {
  overallScore: number;
  contentScore: number;
  voiceDeliveryScore?: number;
  rawVoiceDeliveryScore?: number;
  voiceWeight: number;
  replayCount: number;
  replayPenalty: number;
  voiceEvidenceQuestionCount: number;
  manualFallbackQuestionCount: number;
  referenceOnly: boolean;
  summary: string;
  strengths: string[];
  weaknesses: string[];
  improvementPlan: string[];
  evaluationProfileVersion?: string;
  rubricVersion?: string;
  speechCalibrationVersion?: string;
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
  conversation?: AiInterviewConversation;
}

export type AiInterviewDialogueState =
  | 'SESSION_START'
  | 'OPENING'
  | 'ASK_CORE'
  | 'WAITING_ANSWER'
  | 'ANALYZE_ANSWER'
  | 'ASK_PROBE'
  | 'ASK_CLARIFY'
  | 'ACK_TRANSITION'
  | 'REVIEW_TRANSCRIPTS'
  | 'CLOSING'
  | 'COMPLETED';

export type AiInterviewTurnAnswerStatus =
  | 'NOT_REQUIRED'
  | 'WAITING'
  | 'PROCESSING'
  | 'REVIEWING'
  | 'CONFIRMED'
  | 'SKIPPED';

export interface AiInterviewConversationTurn {
  id: string;
  sequence: number;
  interviewerText: string;
  rawTranscript?: string;
  finalTranscript?: string;
  transcriptEdited: boolean;
  editCount: number;
  answerStatus: AiInterviewTurnAnswerStatus;
  current: boolean;
  answeredAt?: string;
  createdAt?: string;
  transcriptCorrection?: AiInterviewTranscriptCorrection;
}

export type AiInterviewTranscriptCorrectionStatus =
  | 'PENDING'
  | 'CORRECTED'
  | 'UNCHANGED'
  | 'FAILED'
  | 'NOT_REQUIRED';

export type AiInterviewTranscriptCandidateDecision =
  | 'PENDING'
  | 'ACCEPTED'
  | 'REJECTED'
  | 'MANUAL_EDIT'
  | 'AUTO_KEPT';

export interface AiInterviewTranscriptCorrectionItem {
  original: string;
  replacement: string;
  confidence: number;
  reason: string;
}

export interface AiInterviewTranscriptCorrection {
  captureId: string;
  captureVersion: number;
  status: AiInterviewTranscriptCorrectionStatus;
  proposedTranscript?: string;
  correctionCount: number;
  corrections: AiInterviewTranscriptCorrectionItem[];
  candidateDecision?: AiInterviewTranscriptCandidateDecision;
}

export interface AiInterviewTranscriptReviewRequest {
  action: 'ACCEPT_AI' | 'KEEP_CURRENT' | 'MANUAL_EDIT';
  captureId?: string;
  captureVersion?: number;
  transcript?: string;
  expectedEditCount: number;
}

export interface AiInterviewConversation {
  dialogueState: AiInterviewDialogueState;
  version: number;
  currentTurnId?: string;
  expectsAnswer: boolean;
  speechText?: string;
  completedCoreQuestions: number;
  totalCoreQuestions: number;
  errorStage?: string;
  errorCode?: string;
  errorMessage?: string;
  timeline: AiInterviewConversationTurn[];
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
  rawTranscript: string;
  correctedTranscript?: string;
  correctionStatus?: 'PENDING' | 'CORRECTED' | 'UNCHANGED' | 'FAILED' | 'NOT_REQUIRED';
  correctionCount?: number;
  transcriptStatus: 'web_speech' | 'speechmatics_realtime' | 'standardized' | 'fallback_browser';
  dataQuality: string;
  vadMetrics?: HandsFreeVadMetrics;
}

export interface AiInterviewConfirmedAnswer {
  rawTranscript?: string;
  finalTranscript: string;
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

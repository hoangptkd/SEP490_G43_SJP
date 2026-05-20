export interface Interview {
  id: number;
  applicationId: number;
  type: 'AI_INTERVIEW' | 'HUMAN_INTERVIEW';
  scheduledAt: string;
  durationMinutes: number;
  status: 'SCHEDULED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
  createdAt: string;
}

export interface InterviewResponse {
  id: number;
  interviewId: number;
  questionId: number;
  answerText?: string;
  videoUrl?: string;
  aiScore?: number;
  aiFeedback?: string;
  createdAt: string;
}

export interface InterviewFeedback {
  overallScore: number;
  feedback: string;
  strengths: string[];
  weaknesses: string[];
  recommendations: string[];
}

export interface InterviewQuestion {
  id: number;
  category: string;
  questionText: string;
  questionType: 'TEXT' | 'VIDEO' | 'CODING';
  difficultyLevel: 'EASY' | 'MEDIUM' | 'HARD';
}
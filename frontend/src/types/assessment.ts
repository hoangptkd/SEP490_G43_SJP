export interface Assessment {
  id: number;
  jobId?: number;
  candidateId?: number;
  title: string;
  type: 'QUIZ' | 'CODING' | 'MULTIPLE_CHOICE' | 'VIDEO';
  questions: Question[];
  durationMinutes: number;
  status: 'DRAFT' | 'ACTIVE' | 'COMPLETED' | 'EXPIRED';
  score?: number;
  createdAt: string;
  completedAt?: string;
}

export interface Question {
  id: number;
  text: string;
  type: 'MULTIPLE_CHOICE' | 'TEXT' | 'CODING';
  options?: string[];
  correctAnswer?: number | string;
  difficultyLevel: 'EASY' | 'MEDIUM' | 'HARD';
  points: number;
}

export interface AssessmentResult {
  score: number;
  totalQuestions: number;
  correctAnswers: number;
  feedback: string;
  answers: Answer[];
}

export interface Answer {
  questionId: number;
  answer: string | number;
  isCorrect?: boolean;
  points?: number;
}
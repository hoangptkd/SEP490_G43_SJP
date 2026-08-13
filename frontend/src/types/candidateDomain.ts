import type { Job } from './job';

export interface CandidateProfile {
  id: string;
  userId: string;
  fullName?: string;
  headline?: string;
  phone?: string;
  dateOfBirth?: string;
  age?: number;
  location?: string;
  bio?: string;
  experienceYears?: number;
  experienceLevel?: string;
  linkedinUrl?: string;
  portfolioUrl?: string;
  skills: string[];
  education: ProfileSectionItem[];
  workExperience: ProfileSectionItem[];
  projects: ProfileSectionItem[];
  certifications: ProfileSectionItem[];
  applyReady: boolean;
  missingReadinessItems: string[];
}

export interface ProfileSectionItem {
  [key: string]: unknown;
  title: string;
  organization?: string;
  time?: string;
  description?: string;
  url?: string;
  credentialUrl?: string;
}

export interface CvFile {
  id: string;
  originalFileName: string;
  contentType: string;
  fileSize: number;
  defaultCv: boolean;
  deleted: boolean;
  createdAt: string;
}

export interface CvVersion {
  id: string;
  title: string;
  templateKey: string;
  snapshot: Record<string, unknown>;
  updatedAt: string;
}

export interface ApplicationTimeline {
  id: string;
  fromStatus?: string;
  toStatus: string;
  publicNote?: string;
  createdAt: string;
}

export interface PageResult<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface SubmittedResume {
  sourceType: 'uploaded' | 'builder';
  resumeId: string;
  title?: string;
  originalFileName?: string;
  contentType?: string;
  fileSize?: number;
  templateKey?: string;
  builderSnapshot?: Record<string, unknown>;
  sourceUpdatedAt?: string;
  downloadAvailable: boolean;
}

export interface CandidateApplication {
  id: string;
  job: Job;
  candidate?: CandidateProfile;
  cv?: CvFile;
  cvVersion?: CvVersion;
  submittedResume?: SubmittedResume;
  preferredLocation?: string;
  coverLetter?: string;
  status: string;
  submittedAt: string;
  updatedAt: string;
  timeline: ApplicationTimeline[];
  interviews?: InterviewScheduleResponse[];
  jobOffer?: JobOfferResponse;
  aiMatchScore?: number;
  aiMatchAnalysis?: string;
  missingRequirements?: string[];
  scoreBreakdown?: Record<string, number>;
  needRerank?: boolean;
}

export interface NotificationItem {
  id: string;
  type: string;
  title: string;
  message: string;
  read: boolean;
  relatedEntityType?: string;
  relatedEntityId?: string;
  createdAt: string;
}

export interface JobAlert {
  id: string;
  name: string;
  keyword?: string;
  location?: string;
  category?: string;
  jobType?: string;
  workMode?: string;
  minSalary?: number;
  maxSalary?: number;
  frequency: 'DAILY' | 'WEEKLY';
  enabled: boolean;
  lastRunAt?: string;
  createdAt: string;
}

export type JobAlertInput = Omit<JobAlert, 'id' | 'lastRunAt' | 'createdAt'>;

export interface SubscriptionView {
  planCode: string;
  planName: string;
  status: string;
  price: number;
  benefits: string[];
  startedAt?: string;
  expiresAt?: string;
  savedJobsCount: number;
  cvCount: number;
  unreadNotificationsCount: number;
  usages?: Array<{
    featureKey: string;
    label: string;
    used: number;
    limit: number;
    daily: boolean;
  }>;
}

export interface InterviewScheduleRequest {
  scheduledAt: string;
  meetingLink?: string;
  location?: string;
  note?: string;
}

export interface InterviewResultRequest {
  result: 'pass' | 'fail';
  note?: string;
}

export interface InterviewScheduleResponse {
  id: string;
  applicationId: string;
  employerId: string;
  candidateId: string;
  roundNumber: number;
  scheduledAt: string;
  meetingLink?: string;
  location?: string;
  note?: string;
  candidateResponse?: string;
  candidateRescheduleNote?: string;
  employerRescheduleResponse?: string;
  employerRescheduleNote?: string;
  interviewResult?: string;
  interviewResultNote?: string;
  status: string;
  viewedAt?: string;
  respondedAt?: string;
  responseDeadline?: string;
  lastReminderAt?: string;
  employerRescheduleAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface JobOfferRequest {
  positionTitle: string;
  salary?: number;
  salaryCurrency?: string;
  salaryType?: string;
  startDate?: string;
  benefits?: string;
  workingLocation?: string;
  offerLetterUrl?: string;
  employerNote?: string;
}

export interface JobOfferResponse {
  id: string;
  applicationId: string;
  positionTitle: string;
  salary?: number;
  salaryCurrency?: string;
  salaryType?: string;
  startDate?: string;
  benefits?: string;
  workingLocation?: string;
  offerLetterUrl?: string;
  status: string;
  candidateNote?: string;
  employerNote?: string;
}

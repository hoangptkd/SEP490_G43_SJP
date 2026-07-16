import type { Job } from './job';

export interface CandidateProfile {
  id: string;
  userId: string;
  fullName?: string;
  phone?: string;
  location?: string;
  bio?: string;
  skills: string[];
  education: Record<string, unknown>[];
  workExperience: Record<string, unknown>[];
  projects: Record<string, unknown>[];
  certifications: Record<string, unknown>[];
  applyReady: boolean;
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

export interface CandidateApplication {
  id: string;
  job: Job;
  candidate?: CandidateProfile;
  cv?: CvFile;
  cvVersion?: CvVersion;
  status: string;
  submittedAt: string;
  updatedAt: string;
  timeline: ApplicationTimeline[];
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
}

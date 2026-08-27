export interface CandidateProfile {
  id: number;
  userId: number;
  fullName: string;
  bio?: string;
  skills: string[];
  experienceYears: number;
  education?: Education[];
  workExperience?: WorkExperience[];
  createdAt: string;
  updatedAt: string;
}

export interface Education {
  id: number;
  institution: string;
  degree: string;
  field: string;
  startDate: string;
  endDate?: string;
}

export interface WorkExperience {
  id: number;
  company: string;
  position: string;
  startDate: string;
  endDate?: string;
  description?: string;
}

export interface Candidate {
  id: number;
  profile: CandidateProfile;
  email: string;
}
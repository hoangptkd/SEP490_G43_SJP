export interface MatchingResult<T> {
  item: T;
  matchScore: number;
  matchingSkills: string[];
  missingSkills: string[];
}

export interface JobMatchingResult extends MatchingResult<Job> {}
export interface CandidateMatchingResult extends MatchingResult<Candidate> {}

export interface MatchRequest {
  candidateId?: number;
  jobId?: number;
  topK?: number;
}
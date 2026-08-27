package com.sjp.recruitment.service.ai;

import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.model.entity.Job;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MatchingService {

    /**
     * Calculate match score between a candidate and a job
     */
    public double calculateMatchScore(CandidateProfile candidate, Job job) {
        // Skill matching
        List<String> candidateSkills = candidate.getSkills();
        List<String> requiredSkills = job.getRequirements();

        if (requiredSkills.isEmpty()) return 100.0;

        long matchingSkills = candidateSkills.stream()
                .filter(skill -> requiredSkills.stream()
                        .anyMatch(req -> req.equalsIgnoreCase(skill)))
                .count();

        return (double) matchingSkills / requiredSkills.size() * 100;
    }

    /**
     * Get matching skills between candidate and job
     */
    public List<String> getMatchingSkills(CandidateProfile candidate, Job job) {
        return candidate.getSkills().stream()
                .filter(skill -> job.getRequirements().stream()
                        .anyMatch(req -> req.equalsIgnoreCase(skill)))
                .toList();
    }

    /**
     * Get missing skills for a job
     */
    public List<String> getMissingSkills(CandidateProfile candidate, Job job) {
        return job.getRequirements().stream()
                .filter(req -> candidate.getSkills().stream()
                        .noneMatch(skill -> skill.equalsIgnoreCase(req)))
                .toList();
    }
}
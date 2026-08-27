package com.sjp.recruitment.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "candidate_skills")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CandidateSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "job_seeker_id", nullable = false)
    private CandidateProfile candidate;

    @ManyToOne
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    private String level;

    @Column(name = "years_experience")
    private Integer yearsExperience;
}

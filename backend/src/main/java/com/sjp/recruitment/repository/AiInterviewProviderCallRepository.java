package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.AiInterviewProviderCall;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AiInterviewProviderCallRepository extends JpaRepository<AiInterviewProviderCall, UUID> {
}

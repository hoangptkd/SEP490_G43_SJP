package com.sjp.recruitment.service;

import com.sjp.recruitment.model.entity.AiInterviewProviderCall;
import com.sjp.recruitment.repository.AiInterviewProviderCallRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiInterviewTelemetryService {
    private final AiInterviewProviderCallRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID sessionId, String stage, String model, String promptVersion,
                       Integer inputTokens, Integer outputTokens, long latencyMs,
                       boolean success, String errorCode) {
        AiInterviewProviderCall call = new AiInterviewProviderCall();
        call.setSessionId(sessionId);
        call.setStage(stage);
        call.setModel(model);
        call.setPromptVersion(promptVersion);
        call.setInputTokens(inputTokens);
        call.setOutputTokens(outputTokens);
        call.setLatencyMs(latencyMs);
        call.setSuccess(success);
        call.setErrorCode(errorCode);
        repository.save(call);
    }
}

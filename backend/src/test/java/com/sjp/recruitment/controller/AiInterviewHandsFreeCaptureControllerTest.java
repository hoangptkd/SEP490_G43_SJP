package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.response.HandsFreeAnswerCaptureResponse;
import com.sjp.recruitment.service.AiInterviewService;
import com.sjp.recruitment.service.AiInterviewSpeechService;
import com.sjp.recruitment.service.HandsFreeAnswerCaptureService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AiInterviewHandsFreeCaptureControllerTest {
    @Test
    void bindsQuestionScopedMultipartCaptureMetadataAndReturnsProviderNeutralResult() throws Exception {
        AiInterviewService interviewService = mock(AiInterviewService.class);
        AiInterviewSpeechService speechService = mock(AiInterviewSpeechService.class);
        HandsFreeAnswerCaptureService captureService = mock(HandsFreeAnswerCaptureService.class);
        AiInterviewController controller = new AiInterviewController(interviewService, speechService, captureService);
        MockMvc mvc = standaloneSetup(controller).build();
        String sessionId = UUID.randomUUID().toString();
        String questionId = UUID.randomUUID().toString();
        String captureId = UUID.randomUUID().toString();
        HandsFreeAnswerCaptureResponse response = new HandsFreeAnswerCaptureResponse(
                questionId, captureId, 1, "spring bút", "Spring Boot", "Spring Boot",
                "standardized", "AUDIO_GLADIA", null);
        when(captureService.process(eq(sessionId), eq(questionId), eq(captureId), eq(captureId), eq(1),
                anyList(), eq(List.of(0)), eq("spring bút"), eq(List.of(1.25)))).thenReturn(response);
        MockMultipartFile segment = new MockMultipartFile("audioSegments", "segment.webm",
                "audio/webm;codecs=opus", new byte[]{0x1a, 0x45, (byte) 0xdf, (byte) 0xa3});

        var initial = mvc.perform(multipart("/candidate/ai-interviews/sessions/{sessionId}/questions/{questionId}/answer-capture",
                        sessionId, questionId)
                        .file(segment)
                        .header("Idempotency-Key", captureId)
                        .param("captureId", captureId)
                        .param("captureVersion", "1")
                        .param("segmentSequences", "0")
                        .param("browserTranscript", "spring bút")
                        .param("durationSeconds", "1.25"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(initial))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionId").value(questionId))
                .andExpect(jsonPath("$.captureId").value(captureId))
                .andExpect(jsonPath("$.gladiaTranscript").value("Spring Boot"))
                .andExpect(jsonPath("$.finalTranscript").value("Spring Boot"));
        verify(captureService).process(eq(sessionId), eq(questionId), eq(captureId), eq(captureId), eq(1),
                anyList(), eq(List.of(0)), eq("spring bút"), eq(List.of(1.25)));
    }
}

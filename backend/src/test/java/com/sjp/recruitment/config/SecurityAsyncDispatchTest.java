package com.sjp.recruitment.config;

import com.sjp.recruitment.model.dto.response.HandsFreeAnswerCaptureResponse;
import com.sjp.recruitment.model.entity.User;
import com.sjp.recruitment.repository.UserRepository;
import com.sjp.recruitment.service.HandsFreeAnswerCaptureService;
import com.sjp.recruitment.util.JwtUtil;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityAsyncDispatchTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private HandsFreeAnswerCaptureService captureService;

    private String token;
    private String sessionId;
    private String questionId;
    private String captureId;

    @BeforeEach
    void setUp() {
        User candidate = new User();
        candidate.setId(UUID.randomUUID());
        candidate.setEmail("candidate@example.test");
        candidate.setRole(User.UserRole.CANDIDATE);
        candidate.setStatus(User.UserStatus.ACTIVE);
        candidate.setEmailVerified(true);
        candidate.setTokenVersion(0);
        token = jwtUtil.generateToken(candidate);
        when(userRepository.findByEmail(candidate.getEmail())).thenReturn(Optional.of(candidate));

        sessionId = UUID.randomUUID().toString();
        questionId = UUID.randomUUID().toString();
        captureId = UUID.randomUUID().toString();
        when(captureService.process(eq(sessionId), eq(questionId), eq(captureId), eq(captureId), eq(1),
                anyList(), eq(java.util.List.of(0)), eq("Spring Boot"), org.mockito.ArgumentMatchers.isNull(),
                eq(java.util.List.of(1.25))))
                .thenReturn(new HandsFreeAnswerCaptureResponse(
                        questionId, captureId, 1, "Spring Boot", null, "Spring Boot",
                        "Spring Boot", "UNCHANGED", 0, "web_speech", "BROWSER_PLUS_VAD", null));
    }

    @Test
    void authenticatedCandidateCanCompleteAsyncCaptureDispatch() throws Exception {
        var initial = mvc.perform(captureRequest()
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(initial))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.captureId").value(captureId))
                .andExpect(jsonPath("$.rawTranscript").value("Spring Boot"));
    }

    @Test
    void unauthenticatedInitialCaptureRequestRemainsRejected() throws Exception {
        mvc.perform(captureRequest())
                .andExpect(status().is3xxRedirection())
                .andExpect(request().asyncNotStarted());
    }

    @Test
    void unauthenticatedErrorDispatchCanReachErrorController() throws Exception {
        mvc.perform(get("/error")
                        .with(request -> {
                            request.setDispatcherType(DispatcherType.ERROR);
                            return request;
                        })
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/candidate/failing-request")
                        .requestAttr(RequestDispatcher.ERROR_MESSAGE, "test failure"))
                .andExpect(status().isInternalServerError());
    }

    private MockHttpServletRequestBuilder captureRequest() {
        MockMultipartFile segment = new MockMultipartFile(
                "audioSegments", "segment.webm", "audio/webm;codecs=opus",
                new byte[]{0x1a, 0x45, (byte) 0xdf, (byte) 0xa3});
        return multipart("/candidate/ai-interviews/sessions/{sessionId}/questions/{questionId}/answer-capture",
                sessionId, questionId)
                .file(segment)
                .header("Idempotency-Key", captureId)
                .param("captureId", captureId)
                .param("captureVersion", "1")
                .param("segmentSequences", "0")
                .param("browserTranscript", "Spring Boot")
                .param("durationSeconds", "1.25");
    }
}

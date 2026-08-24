package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.response.*;
import com.sjp.recruitment.model.dto.request.AiInterviewTranscriptReviewRequest;
import com.sjp.recruitment.model.enums.TranscriptReviewAction;
import com.sjp.recruitment.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiInterviewControllerTest {

    @Mock private AiInterviewService aiInterviewService;
    @Mock private AiInterviewCvProfileService aiInterviewCvProfileService;
    @Mock private AiInterviewSpeechService aiInterviewSpeechService;
    @Mock private HandsFreeAnswerCaptureService handsFreeAnswerCaptureService;
    @Mock private SpeechmaticsRealtimeTicketService speechmaticsRealtimeTicketService;
    @InjectMocks private AiInterviewController controller;

    @Test
    void classLevelPreAuthorizeRequiresCandidateRole() {
        PreAuthorize annotation = AiInterviewController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(annotation);
        assertEquals("hasRole('CANDIDATE')", annotation.value());
    }

    @Test
    void configStatus_delegatesToService() {
        AiInterviewConfigResponse expected = mock(AiInterviewConfigResponse.class);
        when(aiInterviewService.configStatus()).thenReturn(expected);
        assertSame(expected, controller.configStatus().getBody());
    }

    @Test
    void eligibleApplications_delegatesToService() {
        List<AiInterviewEligibleApplicationResponse> expected = List.of(mock(AiInterviewEligibleApplicationResponse.class));
        when(aiInterviewService.eligibleApplications()).thenReturn(expected);
        assertSame(expected, controller.eligibleApplications().getBody());
    }

    @Test
    void questionSets_delegatesToService() {
        List<AiInterviewQuestionSetResponse> expected = List.of(mock(AiInterviewQuestionSetResponse.class));
        when(aiInterviewService.questionSets()).thenReturn(expected);
        assertSame(expected, controller.questionSets().getBody());
    }

    @Test
    void session_delegatesToService() {
        AiInterviewSessionResponse expected = mock(AiInterviewSessionResponse.class);
        when(aiInterviewService.session("s-1")).thenReturn(expected);
        assertSame(expected, controller.session("s-1").getBody());
    }

    @Test
    void deleteSession_delegatesAndReturnsNoContent() {
        assertEquals(HttpStatus.NO_CONTENT, controller.deleteSession("s-1").getStatusCode());
        verify(aiInterviewService).deleteSession("s-1");
    }

    @Test
    void submitAnswer_delegatesToService() {
        AiInterviewSessionResponse expected = mock(AiInterviewSessionResponse.class);
        when(aiInterviewService.submitAnswer("s-1", "q-1", "My answer")).thenReturn(expected);

        var request = new com.sjp.recruitment.model.dto.request.AiInterviewSubmitAnswerRequest("My answer");
        assertSame(expected, controller.submitAnswer("s-1", "q-1", request).getBody());
    }

    @Test
    void replayQuestion_delegatesToService() {
        AiInterviewSessionResponse expected = mock(AiInterviewSessionResponse.class);
        when(aiInterviewService.replayQuestion("s-1", "q-1")).thenReturn(expected);
        assertSame(expected, controller.replayQuestion("s-1", "q-1").getBody());
    }

    @Test
    void skipQuestion_delegatesToService() {
        AiInterviewSessionResponse expected = mock(AiInterviewSessionResponse.class);
        when(aiInterviewService.skipQuestion("s-1", "q-1")).thenReturn(expected);
        assertSame(expected, controller.skipQuestion("s-1", "q-1").getBody());
    }

    @Test
    void retryConversation_delegatesToService() {
        AiInterviewSessionResponse expected = mock(AiInterviewSessionResponse.class);
        when(aiInterviewService.retryConversation("s-1")).thenReturn(expected);
        assertSame(expected, controller.retryConversation("s-1").getBody());
    }

    @Test
    void reviewConversationTranscript_delegatesToService() {
        AiInterviewSessionResponse expected = mock(AiInterviewSessionResponse.class);
        AiInterviewTranscriptReviewRequest request = new AiInterviewTranscriptReviewRequest(
                TranscriptReviewAction.MANUAL_EDIT, null, null, "Bản đã sửa", 1);
        when(aiInterviewService.reviewConversationTranscript("s-1", "t-1", request))
                .thenReturn(expected);

        assertSame(expected,
                controller.reviewConversationTranscript("s-1", "t-1", request).getBody());
    }

    @Test
    void completeConversationTranscriptReview_delegatesToService() {
        AiInterviewSessionResponse expected = mock(AiInterviewSessionResponse.class);
        when(aiInterviewService.completeConversationTranscriptReview("s-1"))
                .thenReturn(expected);

        assertSame(expected,
                controller.completeConversationTranscriptReview("s-1").getBody());
    }

    @Test
    void retryQuestionGeneration_delegatesToService() {
        AiInterviewSessionResponse expected = mock(AiInterviewSessionResponse.class);
        when(aiInterviewService.retryQuestionGeneration("s-1")).thenReturn(expected);
        assertSame(expected, controller.retryQuestionGeneration("s-1").getBody());
    }

    @Test
    void retryFeedback_delegatesToService() {
        AiInterviewSessionResponse expected = mock(AiInterviewSessionResponse.class);
        when(aiInterviewService.retryFeedback("s-1", "q-1")).thenReturn(expected);
        assertSame(expected, controller.retryFeedback("s-1", "q-1").getBody());
    }

    @Test
    void retrySummary_delegatesToService() {
        AiInterviewSessionResponse expected = mock(AiInterviewSessionResponse.class);
        when(aiInterviewService.retrySummary("s-1")).thenReturn(expected);
        assertSame(expected, controller.retrySummary("s-1").getBody());
    }
}

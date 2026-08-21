package com.sjp.recruitment.controller;

import com.sjp.recruitment.service.AiInterviewSpeechService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiInterviewSpeechStreamControllerTest {

    @Mock private AiInterviewSpeechService aiInterviewSpeechService;
    @InjectMocks private AiInterviewSpeechStreamController controller;

    @Test
    void streamSpeech_returnsStreamingBodyWithCorrectContentType() {
        when(aiInterviewSpeechService.contentTypeForTicket("ticket-1")).thenReturn("audio/mpeg");

        ResponseEntity<StreamingResponseBody> response = controller.streamSpeech("ticket-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.parseMediaType("audio/mpeg"), response.getHeaders().getContentType());
        assertNotNull(response.getBody());
        assertEquals("no-store", response.getHeaders().getCacheControl());
    }

    @Test
    void streamSpeech_setsAcceptRangesNone() {
        when(aiInterviewSpeechService.contentTypeForTicket("ticket-1")).thenReturn("audio/wav");

        ResponseEntity<StreamingResponseBody> response = controller.streamSpeech("ticket-1");

        assertEquals("none", response.getHeaders().getFirst("Accept-Ranges"));
        assertEquals("no", response.getHeaders().getFirst("X-Accel-Buffering"));
    }
}

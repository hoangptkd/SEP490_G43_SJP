package com.sjp.recruitment.controller;

import com.sjp.recruitment.service.AiInterviewSpeechService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/candidate/ai-interviews/speech")
@RequiredArgsConstructor
public class AiInterviewSpeechStreamController {

    private final AiInterviewSpeechService aiInterviewSpeechService;

    @GetMapping("/{ticket}")
    public ResponseEntity<StreamingResponseBody> streamSpeech(@PathVariable String ticket) {
        String contentType = aiInterviewSpeechService.contentTypeForTicket(ticket);
        StreamingResponseBody body = outputStream -> aiInterviewSpeechService.streamSpeech(ticket, outputStream);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.ACCEPT_RANGES, "none")
                .header("X-Accel-Buffering", "no")
                .contentType(MediaType.parseMediaType(contentType))
                .body(body);
    }
}

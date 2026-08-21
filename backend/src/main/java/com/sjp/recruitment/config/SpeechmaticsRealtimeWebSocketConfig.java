package com.sjp.recruitment.config;

import com.sjp.recruitment.service.ai.SpeechmaticsRealtimeWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class SpeechmaticsRealtimeWebSocketConfig implements WebSocketConfigurer {
    private final SpeechmaticsRealtimeWebSocketHandler handler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/ai-interview/transcription")
                .setAllowedOriginPatterns("*");
    }
}

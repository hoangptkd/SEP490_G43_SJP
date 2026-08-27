package com.sjp.recruitment.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.service.SpeechmaticsRealtimeTicketService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpeechmaticsRealtimeWebSocketHandlerTest {
    @Test
    void ignoresProviderErrorWhenBrowserClosesBetweenOpenCheckAndSend() throws Exception {
        WebSocketSession browser = mock(WebSocketSession.class);
        when(browser.isOpen()).thenReturn(true);
        doThrow(new IllegalStateException("WebSocket session has been closed"))
                .when(browser).sendMessage(any());
        SpeechmaticsRealtimeWebSocketHandler handler = handler();
        Object state = newProxyState(browser);

        assertDoesNotThrow(() -> invokeSendProviderError(handler, state));
    }

    @Test
    void reportsSendAsClosedWhenSessionClosesBetweenCheckAndSend() throws Exception {
        WebSocketSession browser = mock(WebSocketSession.class);
        when(browser.isOpen()).thenReturn(true);
        when(browser.getId()).thenReturn("browser-1");
        doThrow(new IllegalStateException("WebSocket session has been closed"))
                .when(browser).sendMessage(any());
        SpeechmaticsRealtimeWebSocketHandler handler = handler();

        assertFalse(invokeSend(handler, browser, new TextMessage("{}")));
    }

    @Test
    void doesNotSendProviderErrorAfterBrowserStartsClosing() throws Exception {
        WebSocketSession browser = mock(WebSocketSession.class);
        SpeechmaticsRealtimeWebSocketHandler handler = handler();
        Object state = newProxyState(browser);
        Field closingField = state.getClass().getDeclaredField("browserClosing");
        closingField.setAccessible(true);
        ((AtomicBoolean) closingField.get(state)).set(true);

        invokeSendProviderError(handler, state);

        verify(browser, never()).sendMessage(any());
    }

    @Test
    void keepsBrowserOpenUntilItConsumesEndOfTranscript() throws Exception {
        WebSocketSession browser = mock(WebSocketSession.class);
        WebSocketSession upstream = mock(WebSocketSession.class);
        when(browser.isOpen()).thenReturn(true);
        when(upstream.isOpen()).thenReturn(true);
        SpeechmaticsRealtimeWebSocketHandler handler = handler();
        Object state = newProxyState(browser);
        Field upstreamField = state.getClass().getDeclaredField("upstream");
        upstreamField.setAccessible(true);
        upstreamField.set(state, upstream);
        Object upstreamHandler = newUpstreamHandler(handler, state);

        invokeUpstreamTextMessage(upstreamHandler, upstream,
                new TextMessage("{\"message\":\"EndOfTranscript\"}"));

        verify(browser).sendMessage(any(TextMessage.class));
        verify(browser, never()).close(any(CloseStatus.class));
        verify(upstream).close(CloseStatus.NORMAL);
    }

    @Test
    void buildsSpeechmaticsStartRecognitionUsingCurrentApiFieldNames() throws Exception {
        AiInterviewProperties properties = new AiInterviewProperties();
        properties.setSpeechmaticsLanguage("vi");
        properties.setSpeechmaticsOperatingPoint("standard");
        properties.setGladiaBaseVocabulary(java.util.List.of("Spring Boot"));
        ObjectMapper objectMapper = new ObjectMapper();
        SpeechmaticsRealtimeWebSocketHandler handler = new SpeechmaticsRealtimeWebSocketHandler(
                properties, mock(SpeechmaticsRealtimeTicketService.class), objectMapper);

        Method method = SpeechmaticsRealtimeWebSocketHandler.class.getDeclaredMethod("startRecognitionMessage");
        method.setAccessible(true);
        TextMessage message = (TextMessage) method.invoke(handler);
        JsonNode config = objectMapper.readTree(message.getPayload()).path("transcription_config");

        assertEquals("standard", config.path("operating_point").asText());
        assertFalse(config.has("model"));
        assertEquals("Spring Boot", config.path("additional_vocab").get(0).path("content").asText());
    }

    @Test
    void rejectsWebSocketBeforeOpeningUpstreamWhenOneTimeTicketIsMissing() throws Exception {
        AiInterviewProperties properties = new AiInterviewProperties();
        SpeechmaticsRealtimeTicketService ticketService = mock(SpeechmaticsRealtimeTicketService.class);
        WebSocketSession browser = mock(WebSocketSession.class);
        when(browser.getUri()).thenReturn(URI.create("ws://localhost/api/ws/ai-interview/transcription"));
        doThrow(new ApiException(HttpStatus.UNAUTHORIZED, "TRANSCRIPTION_TICKET_INVALID",
                "Phiên nhận dạng giọng nói không còn hợp lệ"))
                .when(ticketService).consume(isNull());
        SpeechmaticsRealtimeWebSocketHandler handler = new SpeechmaticsRealtimeWebSocketHandler(
                properties, ticketService, new ObjectMapper());

        handler.afterConnectionEstablished(browser);

        verify(browser).close(new CloseStatus(1008, "Invalid transcription session"));
    }

    private SpeechmaticsRealtimeWebSocketHandler handler() {
        return new SpeechmaticsRealtimeWebSocketHandler(
                new AiInterviewProperties(),
                mock(SpeechmaticsRealtimeTicketService.class),
                new ObjectMapper());
    }

    private Object newProxyState(WebSocketSession browser) throws Exception {
        Class<?> stateClass = Class.forName(SpeechmaticsRealtimeWebSocketHandler.class.getName() + "$ProxyState");
        Constructor<?> constructor = stateClass.getDeclaredConstructor(WebSocketSession.class, long.class);
        constructor.setAccessible(true);
        return constructor.newInstance(browser, 32_000L);
    }

    private void invokeSendProviderError(SpeechmaticsRealtimeWebSocketHandler handler, Object state) throws Exception {
        Method method = SpeechmaticsRealtimeWebSocketHandler.class
                .getDeclaredMethod("sendProviderError", state.getClass());
        method.setAccessible(true);
        method.invoke(handler, state);
    }

    private boolean invokeSend(
            SpeechmaticsRealtimeWebSocketHandler handler,
            WebSocketSession session,
            TextMessage message
    ) throws Exception {
        Method method = SpeechmaticsRealtimeWebSocketHandler.class
                .getDeclaredMethod("send", WebSocketSession.class,
                        org.springframework.web.socket.WebSocketMessage.class);
        method.setAccessible(true);
        return (boolean) method.invoke(handler, session, message);
    }

    private Object newUpstreamHandler(
            SpeechmaticsRealtimeWebSocketHandler handler,
            Object state
    ) throws Exception {
        Class<?> handlerClass = Class.forName(
                SpeechmaticsRealtimeWebSocketHandler.class.getName() + "$UpstreamHandler");
        Constructor<?> constructor = handlerClass.getDeclaredConstructor(
                SpeechmaticsRealtimeWebSocketHandler.class, String.class, state.getClass());
        constructor.setAccessible(true);
        return constructor.newInstance(handler, "browser-1", state);
    }

    private void invokeUpstreamTextMessage(
            Object upstreamHandler,
            WebSocketSession upstream,
            TextMessage message
    ) throws Exception {
        Method method = upstreamHandler.getClass().getDeclaredMethod(
                "handleTextMessage", WebSocketSession.class, TextMessage.class);
        method.setAccessible(true);
        method.invoke(upstreamHandler, upstream, message);
    }
}

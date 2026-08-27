package com.sjp.recruitment.config;

import com.sjp.recruitment.model.entity.User;
import com.sjp.recruitment.repository.UserRepository;
import com.sjp.recruitment.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(
                        "http://localhost:5173",
                        "http://127.0.0.1:5173",
                        "http://localhost:5174",
                        "http://127.0.0.1:5174"
                );
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    authenticate(accessor);
                }
                return message;
            }
        });
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException("WebSocket authentication required");
        }
        String token = authorization.substring(7);
        String email = jwtUtil.extractUsername(token);
        User user = userRepository.findByEmail(email)
                .filter(item -> jwtUtil.validateToken(token))
                .filter(item -> item.isEmailVerified() && item.getStatusEnum() == User.UserStatus.ACTIVE)
                .filter(item -> String.valueOf(item.getId()).equals(jwtUtil.extractStringClaim(token, "id")))
                .filter(item -> {
                    Integer tokenVersion = jwtUtil.extractClaim(token, claims -> claims.get("tokenVersion", Integer.class));
                    return tokenVersion != null && tokenVersion.equals(item.getTokenVersion() == null ? 0 : item.getTokenVersion());
                })
                .orElseThrow(() -> new IllegalArgumentException("Invalid WebSocket token"));
        String role = user.getRoleEnum() == null ? "UNKNOWN" : user.getRoleEnum().name();
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                user.getEmail(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        ));
    }
}

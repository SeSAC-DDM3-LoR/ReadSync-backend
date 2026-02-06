package com.ohgiraffers.backendapi.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker // STOMP 메시징 활성화
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompHandler stompHandler;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        System.out.println("[+] 최초 WebSocket 연결을 위한 등록 Handler");
        // 1. 소켓 연결 엔드포인트: ws://localhost:8080/ws
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // 모든 출처 허용 (CORS 해결)
                .withSockJS(); // SockJS 지원 (브라우저 호환성)
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 2. 메시지 구독 요청 url (Subscribe)
        // /topic: 브로드캐스팅, /queue: 1:1 메시지
        registry.enableSimpleBroker("/topic", "/queue");

        // 3. 메시지 발행 요청 url (Publish)
        // 예: /app/chat/send -> 이 주소로 보내면 @MessageMapping이 처리
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // stompHandler를 인터셉터로 등록하여 CONNECT 메시지를 가로챔
        registration.interceptors(stompHandler);
    }
}

package com.ohgiraffers.backendapi.global.config;

import com.ohgiraffers.backendapi.domain.user.service.UserStatusService;
import com.ohgiraffers.backendapi.global.auth.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 99) // 최우선순위 설정
public class StompHandler implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            log.info("[WebSocket] CONNECT 요청 수신");
            String token = accessor.getFirstNativeHeader("Authorization");

            if (token == null) {
                log.warn("[WebSocket] Authorization 헤더가 없습니다.");
                return message;
            }

            log.info("[WebSocket] Authorization 헤더 확인: {}", token.substring(0, Math.min(20, token.length())) + "...");

            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
                log.info("[WebSocket] Bearer 토큰 추출 완료");

                if (jwtTokenProvider.validateToken(token)) {
                    Authentication auth = jwtTokenProvider.getAuthentication(token);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    accessor.setUser(auth);
                    log.info("[WebSocket] 인증 성공: userId={}", auth.getName());
                } else {
                    log.error("[WebSocket] 토큰 검증 실패");
                }
            } else {
                log.warn("[WebSocket] Bearer 토큰 형식이 아닙니다: {}", token.substring(0, Math.min(10, token.length())));
            }
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            log.info("[WebSocket] SUBSCRIBE 요청: destination={}", accessor.getDestination());
        } else if (StompCommand.SEND.equals(accessor.getCommand())) {
            log.info("[WebSocket] SEND 요청: destination={}", accessor.getDestination());
        } else if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            log.info("[WebSocket] DISCONNECT 요청");
        }

        return message;
    }
}

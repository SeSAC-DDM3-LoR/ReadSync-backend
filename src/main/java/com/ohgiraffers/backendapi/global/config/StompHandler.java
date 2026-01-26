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
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 99)     // 최우선순위 설정
public class StompHandler implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserStatusService userStatusService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // 연결 요청(CONNECT)일 때만 토큰 검사
        if (accessor != null && accessor.getCommand() == StompCommand.CONNECT) {

            // 토큰 추출
            String authorizationHeader = accessor.getFirstNativeHeader("Authorization");

            if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
                log.error("WS Connect Fail: No JWT Token found");
                throw new AccessDeniedException("로그인이 필요합니다.");
            }

            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                String token = authorizationHeader.substring(7);

                if (jwtTokenProvider.validateToken(token)) {
                    // 인증 객체 생성
                    Authentication authentication = jwtTokenProvider.getAuthentication(token);

                    // 소켓 세션 등록
                    accessor.setUser(authentication);

                    log.info("WS Connect Success: User Id = {}", authentication.getName());

                } else {
                    log.warn("WS Connect Fail: Invalid Token");
                    throw new AccessDeniedException("유효하지 않은 토큰입니다.");
                }
            } else  {
                log.warn("WS Connect Fail: No Token");
                throw new AccessDeniedException("토큰이 없습니다.");
            }
        }

        return message;
    }

    @Override
    public void postSend(Message<?> message, MessageChannel channel, boolean sent) {
        StompHeaderAccessor accessor = StompHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() == null) {
            return;
        }

        String sessionId = accessor.getSessionId();

        switch (accessor.getCommand()) {
            case CONNECT:
                // 연결 시: 세션 추가 (ONLINE 상태 유지/갱신)
                if (accessor.getUser() != null) {
                    try {
                        Long userId = Long.valueOf(accessor.getUser().getName());
                        userStatusService.connectSession(userId, sessionId);
                        log.info("STOMP Connected: userId={}, sessionId={}", userId, sessionId);
                    } catch (NumberFormatException e) {
                        log.warn("Invalid userId format in Principal: {}", accessor.getUser().getName());
                    }
                }
                break;

            case DISCONNECT:
                // 연결 해제 시: 세션 제거 (남은 세션이 0일 때만 OFFLINE 처리)
                if (accessor.getUser() != null) {
                    try {
                        Long userId = Long.valueOf(accessor.getUser().getName());
                        userStatusService.disconnectSession(userId, sessionId);
                        log.info("STOMP Disconnected: userId={}, sessionId={}", userId, sessionId);
                    } catch (NumberFormatException e) {
                        log.warn("Invalid userId format during disconnect: {}", accessor.getUser().getName());
                    }
                } else {
                    // 세션이 만료되거나 비정상 종료 시 Principal이 없을 수 있음
                    // 이 경우 DISCONNECT 이벤트를 통해 처리하기 어려울 수 있으나,
                    // Redis TTL(24시간)이 최후의 안전장치 역할을 함
                    log.debug("STOMP Disconnected but User Principal is null. SessionId={}", sessionId);
                }
                break;

            default:
                break;
        }
    }
}

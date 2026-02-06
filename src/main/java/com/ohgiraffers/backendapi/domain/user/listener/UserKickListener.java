package com.ohgiraffers.backendapi.domain.user.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis Pub/Sub 리스너 - 강제 로그아웃 처리
 * "user-kick" 채널을 구독하여 특정 유저의 모든 WebSocket 세션을 강제 종료
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserKickListener implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // Redis에서 받은 메시지 파싱 (userId)
            String channel = new String(message.getChannel());
            String userIdStr = new String(message.getBody());

            log.info("Received kick event from Redis channel [{}]: userId={}", channel, userIdStr);

            // Redis Template이 JSON으로 직렬화할 경우 따옴표가 포함될 수 있음 ("1" -> ""1"")
            userIdStr = userIdStr.replace("\"", ""); // 따옴표 제거

            Long userId = Long.parseLong(userIdStr);

            KickMessage kickMessage = new KickMessage(
                    "FORCE_LOGOUT",
                    "다른 기기에서 로그인하여 현재 세션이 종료됩니다.");

            // 해당 유저에게 강제 로그아웃 메시지 전송
            // /queue 대신 /topic 사용하여 Principal 의존성 제거
            messagingTemplate.convertAndSend(
                    "/topic/user-kick/" + userId,
                    kickMessage);

            log.info("Kick message sent to user: {}", userId);

        } catch (Exception e) {
            log.error("Error processing kick event", e);
        }
    }

    /**
     * 강제 로그아웃 메시지 DTO
     */
    @lombok.Value
    public static class KickMessage {
        String type;
        String message;
    }
}

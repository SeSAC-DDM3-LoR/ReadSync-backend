package com.ohgiraffers.backendapi.domain.chat.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ohgiraffers.backendapi.domain.chat.dto.ChatMessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis Pub/Sub 리스너
 * Redis 채널에서 메시지를 받아 WebSocket 클라이언트들에게 전달
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisMessageListener implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // Redis에서 받은 메시지 파싱
            String channel = new String(message.getChannel());
            String body = new String(message.getBody());

            log.info("Received message from Redis channel [{}]: {}", channel, body);

            // JSON -> ChatMessageResponse 변환
            ChatMessageResponse chatMessage = objectMapper.readValue(body, ChatMessageResponse.class);

            // 채널명에서 roomId 추출 (예: "chatRoom:1" -> "1")
            String roomId = channel.replace("chatRoom:", "");

            // WebSocket 토픽으로 브로드캐스트
            // 클라이언트는 /topic/chatroom/{roomId}를 구독해야 함
            String destination = "/topic/chatroom/" + roomId;
            messagingTemplate.convertAndSend(destination, chatMessage);

            log.info("Broadcasted message to WebSocket topic [{}]", destination);

        } catch (Exception e) {
            log.error("Error processing Redis message", e);
        }
    }
}

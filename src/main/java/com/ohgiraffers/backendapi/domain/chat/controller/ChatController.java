package com.ohgiraffers.backendapi.domain.chat.controller;

import com.ohgiraffers.backendapi.domain.chat.dto.ChatMessageRequest;
import com.ohgiraffers.backendapi.domain.chat.dto.ChatMessageResponse;
import com.ohgiraffers.backendapi.domain.chat.service.ChatLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/chat")
@Tag(name = "Chat API", description = "채팅 관련 API")
public class ChatController {

    private final ChatLogService chatLogService;
    private final org.springframework.core.env.Environment env;
    private final org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    // WebSocket : 실시간 메시지 전송 처리 (Production)
    @MessageMapping("/chat/send")
    public void sendMesaage(@Payload ChatMessageRequest request, Principal principal) {
        if (principal == null) {
            throw new RuntimeException("인증 정보가 없습니다.");
        }
        Long userId = Long.parseLong(principal.getName());
        log.info("WebSocket Message Received: userId={}, roomId={}", userId, request.getRoomId());
        chatLogService.sendMessage(userId, request);
    }

    // [Dev/Local Only] 테스트용 메시지 전송 (인증 없이 동작, DB 저장 X, 오직 Redis Pub/Sub 테스트)
    @MessageMapping("/chat/send/test")
    public void sendTestMessage(@Payload ChatMessageRequest request) {
        // 프로파일 확인 (dev, local 아니면 거부)
        String[] profiles = env.getActiveProfiles();
        boolean isDev = java.util.Arrays.stream(profiles)
                .anyMatch(p -> p.equals("dev") || p.equals("local"));

        if (!isDev) {
            log.warn("Test endpoint called in non-dev environment!");
            return;
        }

        // 테스트 유저 ID (1L)
        Long userId = 1L;
        log.info("[TEST MODE] WebSocket Message Received: userId={}, roomId={} (Bypassing DB)", userId,
                request.getRoomId());

        // 가짜 응답 객체 생성 (DB 저장 없이 바로 리턴)
        ChatMessageResponse dummyResponse = ChatMessageResponse.builder()
                .chatId(0L) // 실제 DB ID 없음
                .senderId(userId)
                .senderName("Test User")
                .senderProfileImage(null)
                .messageType(request.getMessageType())
                .content(request.getContent())
                .imageUrl(request.getImageUrl())
                .sendAt(java.time.LocalDateTime.now())
                .build();

        // Redis 채널에 바로 전송 (ChatLogService 건너뜀)
        String channel = "chatRoom:" + request.getRoomId();
        redisTemplate.convertAndSend(channel, dummyResponse);

        log.info("[TEST MODE] Published to Redis Channel: {}", channel);
    }

    // 채팅방 입장 시 최근 대화 목록 조회(50)
    @Operation(summary = "채팅방 입장 / 최근 대화 목록 조회")
    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> getRecentMessages(
            @Parameter @PathVariable Long roomId) {
        List<ChatMessageResponse> recentMessages = chatLogService.getRecentMessage(roomId);
        return ResponseEntity.ok(recentMessages);
    }

    // 50개 이전의 과거 메시지 조회
    @Operation(summary = "과거 대화 목록 조회")
    @GetMapping("/rooms/{roomId}/messages/history")
    public ResponseEntity<List<ChatMessageResponse>> getOldMessages(
            @Parameter @PathVariable Long roomId,
            @Parameter @RequestParam Long lastChatId) {
        List<ChatMessageResponse> responses = chatLogService.getOldMessage(roomId, lastChatId);
        return ResponseEntity.ok(responses);
    }

}

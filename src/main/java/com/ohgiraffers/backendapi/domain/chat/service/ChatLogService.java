package com.ohgiraffers.backendapi.domain.chat.service;

import com.ohgiraffers.backendapi.domain.chat.dto.ChatMessageRequest;
import com.ohgiraffers.backendapi.domain.chat.dto.ChatMessageResponse;
import com.ohgiraffers.backendapi.domain.chat.entity.ChatLog;
import com.ohgiraffers.backendapi.domain.chat.repository.ChatLogRepository;
import com.ohgiraffers.backendapi.domain.readingroom.entity.ReadingRoom;
import com.ohgiraffers.backendapi.domain.readingroom.enums.ConnectionStatus;
import com.ohgiraffers.backendapi.domain.readingroom.enums.RoomStatus;
import com.ohgiraffers.backendapi.domain.readingroom.repository.ReadingRoomRepository;
import com.ohgiraffers.backendapi.domain.readingroom.repository.RoomParticipantRepository;
import com.ohgiraffers.backendapi.domain.user.entity.User;
import com.ohgiraffers.backendapi.domain.user.repository.UserRepository;
import com.ohgiraffers.backendapi.global.error.CustomException;
import com.ohgiraffers.backendapi.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatLogService {
    private final ChatLogRepository chatLogRepository;
    private final ReadingRoomRepository readingRoomRepository;
    private final UserRepository userRepository;
    private final RoomParticipantRepository roomParticipantRepository;

    // Redis
    private final RedisTemplate<String, Object> redisTemplate;
    // private final SimpMessagingTemplate messagingTemplate; (Removed to fix double
    // message issue)

    // S3 Image Upload
    private final com.ohgiraffers.backendapi.global.service.ImageUploadService imageUploadService;

    // 메시지 전송(DB 저장 -> Redis 채널에 뿌리기)
    @Transactional
    public void sendMessage(Long userId, ChatMessageRequest request) {

        // 유저, 방 검증
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        ReadingRoom readingRoom = readingRoomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

        // 참여자 검증: 활성 참여자만 메시지 전송 가능
        boolean isActiveParticipant = roomParticipantRepository
                .existsByReadingRoomAndUserAndConnectionStatus(
                        readingRoom, user, ConnectionStatus.ACTIVE);
        if (!isActiveParticipant) {
            throw new CustomException(ErrorCode.NOT_ROOM_PARTICIPANT);
        }

        // 종료된 방인지 확인
        if (readingRoom.getStatus() == RoomStatus.FINISHED) { // RoomStatus Enum 가정
            throw new CustomException(ErrorCode.ROOM_FINISHED);
        }

        // 1. 이미지 파일 처리 (S3 업로드)
        String finalImageUrl = null;
        if (request.getImageFile() != null && !request.getImageFile().isEmpty()) {
            // 파일 직접 업로드
            finalImageUrl = imageUploadService.uploadChatImage(request.getImageFile(), request.getRoomId());
            log.info("Image uploaded to S3: {}", finalImageUrl);
        } else if (request.getImageUrl() != null) {
            // URL 직접 사용 (기존 로직 유지)
            finalImageUrl = request.getImageUrl();
        }

        // 2. DB 저장
        ChatLog chatLog;
        if (finalImageUrl != null) {
            chatLog = ChatLog.createImageMessage(readingRoom, user, finalImageUrl);
        } else {
            chatLog = ChatLog.createTextMessage(readingRoom, user, request.getContent());
        }
        ChatLog savedChat = chatLogRepository.save(chatLog);
        ChatMessageResponse response = ChatMessageResponse.from(savedChat);

        // 2. Redis Pub/Sub으로 메시지 발행
        // RedisMessageListener가 이를 수신하여 WebSocket으로 브로드캐스트합니다.
        // (이로써 단일/다중 서버 모두 지원하며 메시지 중복 전송 방지)
        String redisChannel = "chatRoom:" + request.getRoomId();
        redisTemplate.convertAndSend(redisChannel, response);
        log.info("Message sent to Redis Channel [{}]: {}", redisChannel, response.getContent());

    }

    // 채팅방 입장 시 최근 메시지 로딩
    public List<ChatMessageResponse> getRecentMessage(Long roomId) {
        validateRoomExists(roomId);

        // 최근 50개 가져오기
        Pageable limit = PageRequest.of(0, 50);
        List<ChatLog> chatLogs = chatLogRepository.findRecentMessage(roomId, limit);

        // 과거 -> 최신(오름차순)
        Collections.reverse(chatLogs);

        return chatLogs.stream()
                .map(ChatMessageResponse::from)
                .collect(Collectors.toList());
    }

    // 스크롤 올려서 과거 메시지 로딩(커서 페이징)
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getOldMessage(Long roomId, Long lastChatId) {
        validateRoomExists(roomId);

        // lastChatId 보다 오래된 메시지 50개 가져오기
        Pageable limit = PageRequest.of(0, 50);
        List<ChatLog> chatLogs = chatLogRepository.findOldMessage(roomId, lastChatId, limit);

        // 과거 -> 최신(오름차순)
        Collections.reverse(chatLogs);

        return chatLogs.stream()
                .map(ChatMessageResponse::from)
                .collect(Collectors.toList());

    }

    // private help method
    private void validateRoomExists(Long roomId) {
        if (!readingRoomRepository.existsById(roomId)) {
            throw new CustomException(ErrorCode.ROOM_NOT_FOUND);
        }
    }
}

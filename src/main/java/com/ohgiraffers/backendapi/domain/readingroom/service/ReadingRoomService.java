package com.ohgiraffers.backendapi.domain.readingroom.service;

import com.ohgiraffers.backendapi.domain.exp.dto.ExpLogRequestDTO;
import com.ohgiraffers.backendapi.domain.exp.enums.ActivityType;
import com.ohgiraffers.backendapi.domain.exp.service.ExpLogService;
import com.ohgiraffers.backendapi.domain.chapter.entity.Chapter;
import com.ohgiraffers.backendapi.domain.chapter.repository.ChapterRepository;
import com.ohgiraffers.backendapi.domain.library.entity.Library;
import com.ohgiraffers.backendapi.domain.library.repository.LibraryRepository;
import com.ohgiraffers.backendapi.domain.readingroom.dto.CreateRoomRequest;
import com.ohgiraffers.backendapi.domain.readingroom.entity.ReadingRoom;
import com.ohgiraffers.backendapi.domain.readingroom.entity.RoomParticipant;
import com.ohgiraffers.backendapi.domain.readingroom.enums.ConnectionStatus;
import com.ohgiraffers.backendapi.domain.readingroom.enums.InvitationStatus;
import com.ohgiraffers.backendapi.domain.readingroom.enums.RoomStatus;
import com.ohgiraffers.backendapi.domain.readingroom.event.RoomFinishedEvent;
import com.ohgiraffers.backendapi.domain.readingroom.repository.ReadingRoomRepository;
import com.ohgiraffers.backendapi.domain.readingroom.repository.RoomInvitationRepository;
import com.ohgiraffers.backendapi.domain.readingroom.repository.RoomParticipantRepository;
import com.ohgiraffers.backendapi.domain.user.entity.User;
import com.ohgiraffers.backendapi.domain.user.enums.UserActivityStatus;
import com.ohgiraffers.backendapi.domain.user.repository.UserRepository;
import com.ohgiraffers.backendapi.domain.user.service.UserStatusService;
import com.ohgiraffers.backendapi.global.client.TtsClient;
import com.ohgiraffers.backendapi.global.error.CustomException;
import com.ohgiraffers.backendapi.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReadingRoomService {

    private final ReadingRoomRepository readingRoomRepository;
    private final RoomParticipantRepository roomParticipantRepository;
    private final UserRepository userRepository;
    private final LibraryRepository libraryRepository;
    private final ChapterRepository chapterRepository;
    private final com.ohgiraffers.backendapi.domain.chapter.service.ChapterService chapterService;
    private final ExpLogService expLogService;
    private final UserStatusService userStatusService;
    private final ApplicationEventPublisher publisher;
    private final RoomInvitationRepository roomInvitationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final TtsClient ttsClient;

    // 독서룸 생성
    @Transactional
    public Long createRoom(Long hostId, CreateRoomRequest roomRequest) {
        User host = getUser(hostId);
        Library library = libraryRepository.findById(roomRequest.getLibraryId())
                .orElseThrow(() -> new CustomException(ErrorCode.LIBRARY_NOT_FOUND));

        // 방제 지정
        String roomName = roomRequest.getRoomName();
        if (roomName == null || roomName.isBlank()) {
            roomName = library.getBook().getTitle() + " - " + library.getBook().getAuthor();
        }

        // [수정] 관리자는 여러 방 생성 가능, 일반 유저는 1인 1방 제한
        if (host.getRole() != com.ohgiraffers.backendapi.domain.user.enums.UserRole.ADMIN) {
            if (readingRoomRepository.findByHost_IdAndStatusNot(hostId, RoomStatus.FINISHED).isPresent()) {
                throw new CustomException(ErrorCode.ROOM_ALREADY_EXISTS);
            }
        }

        // 해당 책의 첫 번째 챕터 ID 가져오기
        Long bookId = library.getBook().getBookId();
        List<Chapter> chapters = chapterRepository.findByBook_BookIdOrderBySequenceAsc(bookId);
        Integer firstChapterId = chapters.isEmpty() ? 1 : chapters.get(0).getChapterId().intValue();

        ReadingRoom room = ReadingRoom.builder()
                .host(host)
                .library(library)
                .roomName(roomName)
                .voiceType(roomRequest.getVoiceType())
                .maxCapacity(roomRequest.getMaxCapacity())
                .currentChapterId(firstChapterId) // 실제 첫 챕터 ID 사용
                .build();

        ReadingRoom savedRoom = readingRoomRepository.save(room);

        // 참여자 확인
        enterRoom(savedRoom.getRoomId(), hostId);

        return savedRoom.getRoomId();
    }

    // 방 입장
    @Transactional
    public void enterRoom(Long roomId, Long userId) {
        ReadingRoom room = getRoom(roomId);
        User user = getUser(userId);

        Optional<RoomParticipant> existingParticipant = roomParticipantRepository.findByReadingRoomAndUser(room, user);

        if (existingParticipant.isPresent()) {
            RoomParticipant participant = existingParticipant.get();
            // 재입장 여부 확인
            if (participant.isKicked()) {
                throw new CustomException(ErrorCode.KICKED_USER);
            }
            participant.reconnect();

        } else {
            // 방 상태 - 재생중 확인
            if (room.getStatus() == RoomStatus.PLAYING) {
                // 초대장이 있고, 상태가 ACCEPTED(수락됨) 혹은 WAITING(대기중)인 경우 통과
                boolean hasInvitation = roomInvitationRepository.existsByReadingRoomAndReceiverAndStatus(
                        room, user, InvitationStatus.ACCEPTED);

                // 초대장도 없다면 에러 발생 (입장 불가)
                if (!hasInvitation) {
                    throw new CustomException(ErrorCode.ROOM_IS_PLAYING);
                }
            }

            // 현재 인원 확인
            long currentCount = roomParticipantRepository.countByReadingRoomAndConnectionStatus(room,
                    ConnectionStatus.ACTIVE);
            if (currentCount >= room.getMaxCapacity()) {
                throw new CustomException(ErrorCode.ROOM_IS_FULL);
            }

            RoomParticipant newParticipant = RoomParticipant.builder()
                    .readingRoom(room)
                    .user(user)
                    .build();
            roomParticipantRepository.save(newParticipant);
        }

        // 방 입장 시 상태를 '독서중'으로 변경
        userStatusService.updateUserStatus(userId, UserActivityStatus.READING);

        // 실시간 참여자 업데이트 알림 전송
        notifyParticipantUpdate(roomId);
    }

    // 방 퇴장
    @Transactional
    public void leaveRoom(Long roomId, Long userId) {
        ReadingRoom room = getRoom(roomId);
        User user = getUser(userId);
        RoomParticipant participant = getParticipant(room, user);

        if (room.getHost().getId().equals(userId)) {
            // 방장이 퇴장하면 모든 참여자 상태를 ONLINE으로 변경
            List<RoomParticipant> activeParticipants = roomParticipantRepository
                    .findAllByReadingRoomAndConnectionStatus(room, ConnectionStatus.ACTIVE);

            activeParticipants.forEach(p -> {
                if (!p.getUser().getId().equals(userId)) { // 방장 제외
                    userStatusService.updateUserStatus(p.getUser().getId(), UserActivityStatus.ONLINE);
                }
            });

            // 이벤트 발행 (초대장 만료 등 후속 처리)
            publisher.publishEvent(new RoomFinishedEvent(roomId));
            room.finishRoom();
        } else {
            participant.leave();
            // 실시간 참여자 업데이트 알림 전송
            notifyParticipantUpdate(roomId);
        }

        // 방 퇴장 시 상태를 '온라인'으로 변경
        userStatusService.updateUserStatus(userId, UserActivityStatus.ONLINE);
    }

    // 회원 강퇴
    @Transactional
    public void kickUser(Long roomId, Long hostId, Long targetUserId) {
        ReadingRoom room = getRoom(roomId);
        validateHost(room, hostId);

        User targetUser = getUser(targetUserId);
        RoomParticipant targetParticipant = getParticipant(room, targetUser);

        targetParticipant.kick();

        // 실시간 참여자 업데이트 알림 전송
        notifyParticipantUpdate(roomId);
    }

    // 참여자 업데이트 알림 전송
    private void notifyParticipantUpdate(Long roomId) {
        try {
            java.util.Map<String, Object> message = new java.util.HashMap<>();
            message.put("type", "PARTICIPANT_UPDATE");
            message.put("roomId", roomId);
            messagingTemplate.convertAndSend("/topic/room/" + roomId + "/status", message);
        } catch (Exception e) {
            // 로깅 등 에러 처리 (필요시)
            System.err.println("Failed to send participant update: " + e.getMessage());
        }
    }

    // 재생 시작
    @Transactional
    public void startReading(Long roomId, Long hostId) {
        ReadingRoom room = getRoom(roomId);
        validateHost(room, hostId);

        room.updateStatus(RoomStatus.PLAYING);

        // TTS 오디오 URL 가져오기 (AI 서버 호출)
        try {
            String chapterId = "ch" + room.getCurrentChapterId();
            // 문단 ID 형식: p_0001, p_0002 ... (4자리 zero-padding)
            // lastReadPos가 0이면 1(첫 문단)부터 시작, 아니면 현재 문단(lastReadPos) 재개
            int targetPos = (room.getLastReadPos() == 0) ? 1 : room.getLastReadPos();
            String paragraphId = String.format("p_%04d", targetPos);

            // lastReadPos 업데이트 (0인 경우 1로 보정)
            if (room.getLastReadPos() == 0) {
                room.updateLastReadPos(room.getCurrentChapterId(), 1);
            }

            int voiceId = room.getVoiceType().getLuxiaVoiceId(); // VoiceType에서 Luxia Voice ID 가져오기

            // 텍스트 내용 추출
            String text = chapterService.getParagraphText(room.getCurrentChapterId().longValue(), paragraphId);
            if (text == null || text.isEmpty()) {
                log.warn("Text not found for paragraphId: {}", paragraphId);
                text = "내용을 찾을 수 없습니다."; // 기본 멘트
            }

            String audioUrl = ttsClient.getAudioUrl(chapterId, paragraphId, voiceId, text)
                    .block(); // 동기 호출 (필요 시 비동기 처리 가능)

            // WebSocket으로 오디오 URL 전송 (프론트엔드가 구독 중인 /status 토픽으로)
            messagingTemplate.convertAndSend(
                    "/topic/room/" + roomId + "/status",
                    java.util.Map.of(
                            "type", "PLAY_AUDIO",
                            "audioUrl", audioUrl,
                            "chapterId", chapterId,
                            "paragraphId", paragraphId));

            log.info("TTS audio URL sent to room {} (voice: {}): {}", roomId, room.getVoiceType(), audioUrl);
        } catch (Exception e) {
            log.error("Failed to get TTS audio URL for room {}", roomId, e);
            // TTS 실패해도 방 상태는 PLAYING으로 변경 (채팅은 가능하도록)
        }

        notifyRoomStatusChange(roomId, RoomStatus.PLAYING);
    }

    /**
     * 특정 문단의 TTS 오디오 생성 및 브로드캐스트
     * 방장이 문단을 변경하거나 오디오 종료 후 다음 문단으로 이동할 때 호출됨
     */
    @Transactional
    public void playParagraph(Long roomId, Long hostId, String paragraphId) {
        ReadingRoom room = getRoom(roomId);

        // 방장 확인 (null이면 스킵 - WebSocket 인증 문제 시 허용)
        if (hostId != null) {
            validateHost(room, hostId);
        }

        // 현재 읽고 있는 문단 위치 업데이트
        try {
            // p_0001 -> 1 추출
            if (paragraphId.startsWith("p_")) {
                int pos = Integer.parseInt(paragraphId.substring(2));
                room.updateLastReadPos(room.getCurrentChapterId(), pos);
            }
        } catch (NumberFormatException e) {
            log.warn("Failed to parse paragraphId: {}", paragraphId);
        }

        try {
            String chapterId = "ch" + room.getCurrentChapterId();
            int voiceId = room.getVoiceType().getLuxiaVoiceId();

            // 텍스트 내용 추출
            String text = chapterService.getParagraphText(room.getCurrentChapterId().longValue(), paragraphId);
            if (text == null || text.isEmpty()) {
                log.warn("Text not found for paragraphId: {}", paragraphId);
                text = "내용을 찾을 수 없습니다.";
            }

            String audioUrl = ttsClient.getAudioUrl(chapterId, paragraphId, voiceId, text)
                    .block();

            // WebSocket으로 오디오 URL 전송
            messagingTemplate.convertAndSend(
                    "/topic/room/" + roomId + "/status",
                    java.util.Map.of(
                            "type", "PLAY_AUDIO",
                            "audioUrl", audioUrl,
                            "chapterId", chapterId,
                            "paragraphId", paragraphId));

            log.info("TTS audio URL sent for paragraph {} in room {}: {}", paragraphId, roomId, audioUrl);
        } catch (Exception e) {
            log.error("Failed to play paragraph {} in room {}", paragraphId, roomId, e);
        }
    }

    // 독서 일시정지/재개
    @Transactional
    public void pauseReading(Long roomId, Long hostId) {
        ReadingRoom room = getRoom(roomId);
        validateHost(room, hostId);

        // PLAYING <-> PAUSED 토글
        if (room.getStatus() == RoomStatus.PLAYING) {
            room.updateStatus(RoomStatus.PAUSED);
            notifyRoomStatusChange(roomId, RoomStatus.PAUSED);
        } else if (room.getStatus() == RoomStatus.PAUSED) {
            room.updateStatus(RoomStatus.PLAYING);
            notifyRoomStatusChange(roomId, RoomStatus.PAUSED);
        } else {
            throw new CustomException(ErrorCode.INVALID_REQUEST_STATUS, "재생 중이거나 일시정지 상태에서만 사용 가능합니다.");
        }
    }

    // 재생 속도 변경
    @Transactional
    public void updatePlaySpeed(Long roomId, Long hostId, java.math.BigDecimal speed) {
        ReadingRoom room = getRoom(roomId);
        validateHost(room, hostId);
        room.changePlaySpeed(speed);

        notifyRoomSettingsChange(roomId, "SPEED", speed.toString());
    }

    // 목소리 변경
    @Transactional
    public void updateVoiceType(Long roomId, Long hostId,
            com.ohgiraffers.backendapi.domain.readingroom.enums.VoiceType voiceType) {
        ReadingRoom room = getRoom(roomId);
        validateHost(room, hostId);
        room.setVoiceType(voiceType);

        notifyRoomSettingsChange(roomId, "VOICE", voiceType.name());
    }

    // 설정 변경 알림
    private void notifyRoomSettingsChange(Long roomId, String setting, String value) {
        try {
            java.util.Map<String, Object> message = new java.util.HashMap<>();
            message.put("type", "SETTINGS_UPDATE");
            message.put("roomId", roomId);
            message.put("setting", setting);
            message.put("value", value);
            messagingTemplate.convertAndSend("/topic/room/" + roomId + "/status", message);
        } catch (Exception e) {
            log.error("Failed to send settings update", e);
        }
    }

    // 목표 달성 및 종료
    @Transactional
    public void finishReading(Long roomId, Long hostId) {
        ReadingRoom room = getRoom(roomId);
        validateHost(room, hostId);

        // 경험치 지급 로직: 활성 상태인 모든 참여자에게 EXP 지급
        List<RoomParticipant> activeParticipants = roomParticipantRepository
                .findAllByReadingRoomAndConnectionStatus(room, ConnectionStatus.ACTIVE);

        activeParticipants.forEach(participant -> {
            ExpLogRequestDTO expRequest = ExpLogRequestDTO.builder()
                    .userId(participant.getUser().getId())
                    .activityType(ActivityType.READ_BOOK)
                    .categoryId(room.getLibrary().getBook().getCategory().getCategoryId())
                    .targetId(room.getLibrary().getBook().getBookId())
                    .referenceId(room.getRoomId())
                    .build();

            try {
                expLogService.giveExperience(expRequest);
            } catch (IllegalArgumentException e) {
                // 중복 지급 방지: 이미 경험치를 받은 경우 무시
                // 로그만 남기고 계속 진행
            }
        });

        // 방 종료 이벤트 발행 (초대장 만료 등 후속 처리)
        publisher.publishEvent(new RoomFinishedEvent(roomId));
        room.finishRoom();
    }

    // --- Private Helper Methods ---

    private ReadingRoom getRoom(Long roomId) {
        return readingRoomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private RoomParticipant getParticipant(ReadingRoom room, User user) {
        return roomParticipantRepository.findByReadingRoomAndUser(room, user)
                .orElseThrow(() -> new CustomException(ErrorCode.PARTICIPANT_NOT_FOUND));
    }

    private void validateHost(ReadingRoom room, Long hostId) {
        if (!room.getHost().getId().equals(hostId)) {
            throw new CustomException(ErrorCode.NOT_HOST);
        }
    }

    // 상태 변경 알림 메서드
    private void notifyRoomStatusChange(Long roomId, RoomStatus status) {
        try {
            java.util.Map<String, Object> message = new java.util.HashMap<>();
            message.put("type", "STATUS_CHANGE");
            message.put("roomId", roomId);
            message.put("status", status);
            messagingTemplate.convertAndSend("/topic/room/" + roomId + "/status", message);
        } catch (Exception e) {
            System.err.println("Failed to send status update: " + e.getMessage());
        }
    }

    // ===== 조회 API =====

    /**
     * 전체 활성화된 독서룸 목록 조회
     */
    public List<com.ohgiraffers.backendapi.domain.readingroom.dto.ReadingRoomResponse> getAllActiveRooms() {
        // Fetch Join으로 연관 엔티티 한번에 로딩 (N+1 문제 및 LazyLoading 에러 해결)
        List<ReadingRoom> rooms = readingRoomRepository.findAllActiveWithFetchJoin(RoomStatus.FINISHED);

        return rooms.stream()
                .map(room -> {
                    int participantCount = (int) roomParticipantRepository
                            .countByReadingRoomAndConnectionStatus(room, ConnectionStatus.ACTIVE);
                    return com.ohgiraffers.backendapi.domain.readingroom.dto.ReadingRoomResponse.from(room,
                            participantCount);
                })
                .toList();
    }

    /**
     * 독서룸 상세 조회
     */
    public com.ohgiraffers.backendapi.domain.readingroom.dto.ReadingRoomResponse getRoomDetail(Long roomId) {
        ReadingRoom room = getRoom(roomId);
        int participantCount = (int) roomParticipantRepository
                .countByReadingRoomAndConnectionStatus(room, ConnectionStatus.ACTIVE);
        return com.ohgiraffers.backendapi.domain.readingroom.dto.ReadingRoomResponse.from(room, participantCount);
    }

    /**
     * 내가 참여 중인 독서룸 목록 조회
     */
    public List<com.ohgiraffers.backendapi.domain.readingroom.dto.ReadingRoomResponse> getMyRooms(Long userId) {
        User user = getUser(userId);
        List<RoomParticipant> myParticipations = roomParticipantRepository
                .findByUserAndConnectionStatus(user, ConnectionStatus.ACTIVE);

        return myParticipations.stream()
                .map(participant -> {
                    ReadingRoom room = participant.getReadingRoom();
                    int participantCount = (int) roomParticipantRepository
                            .countByReadingRoomAndConnectionStatus(room, ConnectionStatus.ACTIVE);
                    return com.ohgiraffers.backendapi.domain.readingroom.dto.ReadingRoomResponse.from(room,
                            participantCount);
                })
                .toList();
    }

    /**
     * 참여자 목록 조회
     */
    public List<com.ohgiraffers.backendapi.domain.readingroom.dto.ParticipantResponse> getParticipants(Long roomId) {
        ReadingRoom room = getRoom(roomId);
        List<RoomParticipant> participants = roomParticipantRepository
                .findAllByReadingRoomAndConnectionStatus(room, ConnectionStatus.ACTIVE);

        return participants.stream()
                .map(participant -> com.ohgiraffers.backendapi.domain.readingroom.dto.ParticipantResponse
                        .from(participant, room.getHost().getId()))
                .toList();
    }
}

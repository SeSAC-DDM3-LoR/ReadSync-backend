package com.ohgiraffers.backendapi.domain.readingroom.service;

import com.ohgiraffers.backendapi.domain.exp.dto.ExpLogRequestDTO;
import com.ohgiraffers.backendapi.domain.exp.enums.ActivityType;
import com.ohgiraffers.backendapi.domain.exp.service.ExpLogService;
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
import com.ohgiraffers.backendapi.global.error.CustomException;
import com.ohgiraffers.backendapi.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReadingRoomService {

    private final ReadingRoomRepository readingRoomRepository;
    private final RoomParticipantRepository roomParticipantRepository;
    private final UserRepository userRepository;
    private final LibraryRepository libraryRepository;
    private final ExpLogService expLogService;
    private final UserStatusService userStatusService;
    private final ApplicationEventPublisher publisher;
    private final RoomInvitationRepository roomInvitationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // 독서룸 생성
    @Transactional
    public Long createRoom(Long hostId, CreateRoomRequest roomRequest) {
        User host = getUser(hostId);
        Library library = libraryRepository.findById(roomRequest.getLibraryId())
                .orElseThrow(() -> new CustomException(ErrorCode.LIBRARY_NOT_FOUND));

        if (readingRoomRepository.findByHost_IdAndStatusNot(hostId, RoomStatus.FINISHED).isPresent()) {
            throw new CustomException(ErrorCode.ROOM_ALREADY_EXISTS);
        }

        // 방제 지정
        String roomName = roomRequest.getRoomName();
        if (roomName == null || roomName.isBlank()) {
            roomName = library.getBook().getTitle() + " - " + library.getBook().getAuthor();
        }

        ReadingRoom room = ReadingRoom.builder()
                .host(host)
                .library(library)
                .roomName(roomName)
                .voiceType(roomRequest.getVoiceType())
                .maxCapacity(roomRequest.getMaxCapacity())
                .currentChapterId(roomRequest.getCurrentChapterId())
                .build();

        ReadingRoom savedRoom = readingRoomRepository.save(room);

        // 참여자 확인
        enterRoom(hostId, savedRoom.getRoomId());

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
                        room, user, InvitationStatus.ACCEPTED
                );

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
            messagingTemplate.convertAndSend("/topic/room/" + roomId, message);
        } catch (Exception e) {
            // 로깅 등 에러 처리 (필요시)
            System.err.println("Failed to send participant update: " + e.getMessage());
        }
    }

    // 재생속도 변경
    @Transactional
    public void updatePlaySpeed(Long roomId, Long hostId, BigDecimal speed) {
        ReadingRoom room = getRoom(roomId);
        validateHost(room, hostId);

        room.changePlaySpeed(speed);
    }

    // 재생 시작
    @Transactional
    public void startReading(Long roomId, Long hostId) {
        ReadingRoom room = getRoom(roomId);
        validateHost(room, hostId);

        room.updateStatus(RoomStatus.PLAYING);

        notifyRoomStatusChange(roomId, RoomStatus.PLAYING);
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
            messagingTemplate.convertAndSend("/topic/room/" + roomId, message);
        } catch (Exception e) {
            System.err.println("Failed to send status update: " + e.getMessage());
        }
    }

    // ===== 조회 API =====

    /**
     * 전체 활성화된 독서룸 목록 조회
     */
    public List<com.ohgiraffers.backendapi.domain.readingroom.dto.ReadingRoomResponse> getAllActiveRooms() {
        List<ReadingRoom> rooms = readingRoomRepository.findAll().stream()
                .filter(room -> room.getStatus() != RoomStatus.FINISHED)
                .toList();

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

package com.ohgiraffers.backendapi.domain.readingroom.service;

import com.ohgiraffers.backendapi.domain.readingroom.entity.ReadingRoom;
import com.ohgiraffers.backendapi.domain.readingroom.entity.RoomInvitation;
import com.ohgiraffers.backendapi.domain.readingroom.enums.ConnectionStatus;
import com.ohgiraffers.backendapi.domain.readingroom.enums.InvitationStatus;
import com.ohgiraffers.backendapi.domain.readingroom.enums.RoomStatus;
import com.ohgiraffers.backendapi.domain.readingroom.event.RoomFinishedEvent;
import com.ohgiraffers.backendapi.domain.readingroom.repository.ReadingRoomRepository;
import com.ohgiraffers.backendapi.domain.readingroom.repository.RoomInvitationRepository;
import com.ohgiraffers.backendapi.domain.readingroom.repository.RoomParticipantRepository;
import com.ohgiraffers.backendapi.domain.user.entity.User;
import com.ohgiraffers.backendapi.domain.user.repository.UserRepository;
import com.ohgiraffers.backendapi.global.error.CustomException;
import com.ohgiraffers.backendapi.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomInvitationService {

    private final RoomInvitationRepository invitationRepository;
    private final ReadingRoomRepository readingRoomRepository;
    private final RoomParticipantRepository roomParticipantRepository;
    private final UserRepository userRepository;

    private final ReadingRoomService readingRoomService;

    // 초대장 발송
    @Transactional
    public void inviteUser(Long roomId, Long hostId, Long targetId) {
        ReadingRoom room = readingRoomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

        // 방장 권한 체크
        if (!room.getHost().getId().equals(hostId)) {
            throw new CustomException(ErrorCode.NOT_HOST);
        }

        // 재생 중 체크
        if (room.getStatus() == RoomStatus.PLAYING) {
            throw new CustomException(ErrorCode.INVITATION_NOT_ALLOWED_PLAYING);
        }

        // 인원 수 체크
        if (roomParticipantRepository.countByReadingRoomAndConnectionStatus(room, ConnectionStatus.ACTIVE) >= room
                .getMaxCapacity()) {
            throw new CustomException(ErrorCode.INVITATION_NOT_ALLOWED_FULL);
        }

        User sender = room.getHost();
        User receiver = userRepository.findById(targetId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 이미 초대를 받았는지 체크하기
        if (invitationRepository.findByReadingRoomAndReceiverAndStatus(room, receiver, InvitationStatus.PENDING)
                .isPresent()) {
            throw new CustomException(ErrorCode.ALREADY_INVITED);
        }

        RoomInvitation invitation = RoomInvitation.builder()
                .readingRoom(room)
                .sender(sender)
                .receiver(receiver)
                .build();

        invitationRepository.save(invitation);
    }

    // 초대장 수락
    @Transactional
    public void acceptInvitation(Long invitationId, Long userId) {
        // 초대장 찾기
        // 초대장이 만들어진 적이 없거나, 보낸 사람이 취소를 했거나, 거절 및 수락으로 목록에서 사라진 경우
        RoomInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVITATION_NOT_FOUND));

        // 본인 확인 및 만료 체크
        // 초대장 유효시간이 지났거나, 만료 상태로 변경된 경우
        validateReceiver(invitation, userId);
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new CustomException(ErrorCode.INVITATION_EXPIRED);
        }

        invitation.accept();

        // 방 입장 로직 호출
        readingRoomService.enterRoom(invitation.getReadingRoom().getRoomId(), userId);

    }

    // 초대장 거절
    @Transactional
    public void rejectInvitation(Long invitationId, Long userId) {
        RoomInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVITATION_NOT_FOUND));

        validateReceiver(invitation, userId);

        invitation.reject();
    }

    // 독서룸 종료 시 해당 방의 모든 PENDING 초대장을 EXPIRED로 변경 (이벤트 기반)
    @EventListener
    @Transactional
    public void handleRoomFinishedEvent(RoomFinishedEvent event) {
        expireInvitationsForRoom(event.roomId());
    }

    // 독서룸 종료 시 해당 방의 모든 PENDING 초대장을 EXPIRED로 변경
    @Transactional
    public void expireInvitationsForRoom(Long roomId) {
        ReadingRoom room = readingRoomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

        List<RoomInvitation> pendingInvitations = invitationRepository
                .findByReadingRoomAndStatus(room, InvitationStatus.PENDING);

        pendingInvitations.forEach(RoomInvitation::expire);
    }

    // --- Private Helper Methods ---
    private void validateReceiver(RoomInvitation invitation, Long userId) {
        if (!invitation.getReceiver().getId().equals(userId)) {
            throw new CustomException(ErrorCode.NOT_YOUR_INVITATION);
        }
    }

    // ===== 조회 API =====

    /**
     * 받은 초대장 목록 조회
     */
    public List<com.ohgiraffers.backendapi.domain.readingroom.dto.InvitationResponse> getReceivedInvitations(
            Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        List<RoomInvitation> invitations = invitationRepository.findByReceiver(user);
        return invitations.stream()
                .map(com.ohgiraffers.backendapi.domain.readingroom.dto.InvitationResponse::from)
                .toList();
    }

    /**
     * 보낸 초대장 목록 조회
     */
    public List<com.ohgiraffers.backendapi.domain.readingroom.dto.InvitationResponse> getSentInvitations(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        List<RoomInvitation> invitations = invitationRepository.findBySender(user);
        return invitations.stream()
                .map(com.ohgiraffers.backendapi.domain.readingroom.dto.InvitationResponse::from)
                .toList();
    }
}

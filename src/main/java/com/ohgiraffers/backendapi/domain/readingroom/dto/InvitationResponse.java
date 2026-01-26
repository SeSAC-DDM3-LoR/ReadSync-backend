package com.ohgiraffers.backendapi.domain.readingroom.dto;

import com.ohgiraffers.backendapi.domain.readingroom.entity.RoomInvitation;
import com.ohgiraffers.backendapi.domain.readingroom.enums.InvitationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class InvitationResponse {

    private Long invitationId;
    private Long roomId;
    private String roomName;
    private Long senderId;
    private String senderName;
    private Long receiverId;
    private String receiverName;
    private InvitationStatus status;
    private LocalDateTime sentAt;

    public static InvitationResponse from(RoomInvitation invitation) {
        return InvitationResponse.builder()
                .invitationId(invitation.getInvitationId())
                .roomId(invitation.getReadingRoom().getRoomId())
                .roomName(invitation.getReadingRoom().getRoomName())
                .senderId(invitation.getSender().getId())
                .senderName(invitation.getSender().getUserInformation().getNickname())
                .receiverId(invitation.getReceiver().getId())
                .receiverName(invitation.getReceiver().getUserInformation().getNickname())
                .status(invitation.getStatus())
                .sentAt(invitation.getSentAt())
                .build();
    }
}

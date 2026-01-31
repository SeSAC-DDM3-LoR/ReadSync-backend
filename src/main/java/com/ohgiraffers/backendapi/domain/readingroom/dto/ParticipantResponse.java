package com.ohgiraffers.backendapi.domain.readingroom.dto;

import com.ohgiraffers.backendapi.domain.readingroom.entity.RoomParticipant;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ParticipantResponse {

    private Long participantId;
    private Long userId;
    private String nickname;
    private String profileImage;
    private Boolean isHost;
    private LocalDateTime joinedAt;

    public static ParticipantResponse from(RoomParticipant participant, Long hostId) {
        return ParticipantResponse.builder()
                .participantId(participant.getParticipantId())
                .userId(participant.getUser().getId())
                .nickname(participant.getUser().getUserInformation().getNickname())
                .profileImage(participant.getUser().getUserInformation().getProfileImage())
                .isHost(participant.getUser().getId().equals(hostId))
                .joinedAt(participant.getCreatedAt())
                .build();
    }
}

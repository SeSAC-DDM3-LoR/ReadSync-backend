package com.ohgiraffers.backendapi.domain.readingroom.dto;

import com.ohgiraffers.backendapi.domain.readingroom.entity.ReadingRoom;
import com.ohgiraffers.backendapi.domain.readingroom.enums.RoomStatus;
import com.ohgiraffers.backendapi.domain.readingroom.enums.VoiceType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class ReadingRoomResponse {

    private Long roomId;
    private String roomName;
    private Long hostId;
    private String hostName;
    private String bookTitle;
    private RoomStatus status;
    private VoiceType voiceType;
    private BigDecimal playSpeed;
    private Integer maxCapacity;
    private Integer currentParticipants;
    private Integer currentChapterId;

    public static ReadingRoomResponse from(ReadingRoom room, Integer participantCount) {
        return ReadingRoomResponse.builder()
                .roomId(room.getRoomId())
                .roomName(room.getRoomName())
                .hostId(room.getHost().getId())
                .hostName(room.getHost().getUserInformation().getNickname())
                .bookTitle(room.getLibrary().getBook().getTitle())
                .status(room.getStatus())
                .voiceType(room.getVoiceType())
                .playSpeed(room.getPlaySpeed())
                .maxCapacity(room.getMaxCapacity())
                .currentParticipants(participantCount)
                .currentChapterId(room.getCurrentChapterId())
                .build();
    }
}

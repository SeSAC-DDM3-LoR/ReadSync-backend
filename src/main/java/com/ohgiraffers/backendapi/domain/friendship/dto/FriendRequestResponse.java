package com.ohgiraffers.backendapi.domain.friendship.dto;

import com.ohgiraffers.backendapi.domain.friendship.entity.Friendship;
import com.ohgiraffers.backendapi.domain.friendship.enums.FriendshipStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class FriendRequestResponse {

    private Long friendshipId;
    private Long requesterId;
    private String requesterName;
    private Long addresseeId;
    private String addresseeName;
    private FriendshipStatus status;
    private LocalDateTime createdAt;

    public static FriendRequestResponse from(Friendship friendship) {
        return FriendRequestResponse.builder()
                .friendshipId(friendship.getFriendshipId())
                .requesterId(friendship.getRequester().getId())
                .requesterName(friendship.getRequester().getUserInformation().getNickname())
                .addresseeId(friendship.getAddressee().getId())
                .addresseeName(friendship.getAddressee().getUserInformation().getNickname())
                .status(friendship.getStatus())
                .createdAt(friendship.getCreatedAt())
                .build();
    }
}

package com.ohgiraffers.backendapi.domain.blacklist.dto;

import com.ohgiraffers.backendapi.domain.blacklist.entity.Blacklist;
import com.ohgiraffers.backendapi.domain.blacklist.enums.BlacklistType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class BlacklistResponse {

    private Long blacklistId;
    private Long userId;
    private String userLoginId;
    private BlacklistType type;
    private String reason;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private boolean isActive;

    public static BlacklistResponse from(Blacklist blacklist) {
        return BlacklistResponse.builder()
                .blacklistId(blacklist.getId())
                .userId(blacklist.getUser().getId())
                .userLoginId(blacklist.getUser().getLoginId())
                .type(blacklist.getType())
                .reason(blacklist.getReason())
                .startDate(blacklist.getStartDate())
                .endDate(blacklist.getEndDate())
                .isActive(blacklist.isActive())
                .build();
    }

    // 내 제재 상태 응답 DTO
    @Getter
    @Builder
    public static class MyStatus {
        private boolean isBanned;
        private BlacklistType type;
        private String reason;
        private LocalDateTime endDate;
    }
}
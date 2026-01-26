package com.ohgiraffers.backendapi.domain.blacklist.dto;

import com.ohgiraffers.backendapi.domain.blacklist.enums.BlacklistType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

public class BlacklistRequest {

    @Getter
    @Setter
    public static class Create {
        @NotNull(message = "대상 유저 ID는 필수입니다.")
        @Schema(description = "제재할 유저의 ID", example = "5")
        private Long targetUserId;

        @NotNull(message = "제재 유형은 필수입니다.")
        @Schema(description = "제재 유형 (BAN, MUTE 등)", example = "BAN")
        private BlacklistType type;

        @Schema(description = "제재 사유", example = "지속적인 욕설 사용")
        private String reason;

        @Schema(description = "제재 기간 (일 단위)", example = "7")
        private int durationDays;
    }
}
package com.ohgiraffers.backendapi.domain.credit.dto;

import com.ohgiraffers.backendapi.domain.credit.entity.Credit;
import com.ohgiraffers.backendapi.domain.credit.enums.CreditStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class CreditResponse {

    // ... (기존 DTO들) ...

    // ▼ [어드민용] 상세 크레딧 로그 DTO
    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AdminCreditLog {
        private Long creditId;
        private Long userId;
        private String nickname;       // 유저 이름 식별용
        private String creditTypeName; // "이벤트", "유료" 등
        private Integer amount;
        private CreditStatus status;   // ACTIVE, USED, EXPIRED
        private LocalDateTime createdAt; // 지급일
        private LocalDateTime expiredAt; // 만료일
        private LocalDateTime usedAt;    // 사용일 (없으면 null)

        public static AdminCreditLog from(Credit credit) {
            return AdminCreditLog.builder()
                    .creditId(credit.getId())
                    .userId(credit.getUser().getId())
                    .nickname(credit.getUser().getUserInformation().getNickname())
                    .creditTypeName(credit.getCreditType().getName())
                    .amount(credit.getAmount())
                    .status(credit.getStatus())
                    .createdAt(credit.getCreatedAt())
                    .expiredAt(credit.getExpiredAt())
                    .usedAt(credit.getUsedAt())
                    .build();
        }
    }
}
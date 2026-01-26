package com.ohgiraffers.backendapi.domain.subscription.dto;

import com.ohgiraffers.backendapi.domain.subscription.enums.SubscriptionStatus;
import com.ohgiraffers.backendapi.domain.subscription.entity.Subscription;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 구독 정보 응답 DTO
 */
@Getter
@Builder
public class SubscriptionResponse {
    private Long subId;
    private String planName;
    private BigDecimal price;
    private SubscriptionStatus status;
    private LocalDateTime nextBillingDate;
    private LocalDateTime startedAt;

    public static SubscriptionResponse from(Subscription subscription) {
        return SubscriptionResponse.builder()
                .subId(subscription.getSubId())
                .planName(subscription.getPlan().getPlanName())
                .price(subscription.getPlan().getPrice())
                .status(subscription.getSubscriptionStatus())
                .nextBillingDate(subscription.getNextBillingDate())
                .startedAt(subscription.getStartedAt())
                .build();
    }
}

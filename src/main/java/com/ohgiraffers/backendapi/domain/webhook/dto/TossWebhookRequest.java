package com.ohgiraffers.backendapi.domain.webhook.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class TossWebhookRequest {
    private String eventType;
    private String createdAt;
    private Data data;

    @Getter
    @Setter
    @ToString
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        private String paymentKey;
        private String orderId;
        private String status;
        private String secret; // 가상계좌 웹훅용
    }
}

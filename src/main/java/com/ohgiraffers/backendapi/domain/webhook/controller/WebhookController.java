package com.ohgiraffers.backendapi.domain.webhook.controller;

import com.ohgiraffers.backendapi.domain.payment.service.PaymentService;
import com.ohgiraffers.backendapi.domain.webhook.dto.TossWebhookRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/v1/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final PaymentService paymentService;

    @PostMapping("/toss")
    public ResponseEntity<Void> handleTossWebhook(@RequestBody TossWebhookRequest request) {
        log.info("Received Toss Webhook: {}", request);

        try {
            paymentService.handleWebhook(request);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error handling Toss Webhook", e);
            // 토스 페이먼츠는 200이 아니면 재시도하므로, 비즈니스 로직 에러 시 500 반환하여 재시도 유도 고려
            // 단, 데이터 문제 등 재시도해도 해결되지 않을 에러는 200 처리하거나 별도 로깅 필요
            // 여기서는 일단 에러 로그 남기고 500 반환
            return ResponseEntity.internalServerError().build();
        }
    }
}

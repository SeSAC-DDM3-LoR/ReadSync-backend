package com.ohgiraffers.backendapi.domain.reading.controller;

import com.ohgiraffers.backendapi.domain.reading.dto.ReadingPulseRequestDTO;
import com.ohgiraffers.backendapi.global.common.annotation.CurrentUserId;
import com.ohgiraffers.backendapi.infra.event.ReadingEvent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Reading", description = "독서 상태 실시간 처리 API")
@RestController
@RequestMapping("/v1/reading")
@RequiredArgsConstructor
public class ReadingEventController {

    private final ApplicationEventPublisher eventPublisher;

    @Operation(summary = "독서 펄스 수신", description = "뷰어에서 5분 주기 또는 종료 시 보내는 독서 데이터를 처리합니다.")
    @PostMapping("/pulse")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<Void> receivePulse(
            @CurrentUserId Long userId,
            @RequestBody ReadingPulseRequestDTO request) {

        // 이벤트를 발행하고 즉시 200 OK 응답 (나머지 로직은 Listener가 처리)
        eventPublisher.publishEvent(new ReadingEvent(userId, request));

        return ResponseEntity.ok().build();
    }
}

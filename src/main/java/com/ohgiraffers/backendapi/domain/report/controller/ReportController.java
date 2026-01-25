package com.ohgiraffers.backendapi.domain.report.controller;

import com.ohgiraffers.backendapi.domain.report.dto.ReportRequest;
import com.ohgiraffers.backendapi.domain.report.dto.ReportResponse;
import com.ohgiraffers.backendapi.domain.report.entity.Report;
import com.ohgiraffers.backendapi.domain.report.enums.ReportStatus;
import com.ohgiraffers.backendapi.domain.report.service.ReportService;
import com.ohgiraffers.backendapi.global.common.annotation.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Report", description = "채팅 신고 관리 API")
@RestController
@RequestMapping("/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    // [일반 유저] 채팅 신고
    @PostMapping("/chat")
    @Operation(summary = "채팅 신고 접수", description = "부적절한 채팅을 신고합니다.")
    public ResponseEntity<String> createChatReport(
            @CurrentUserId Long reporterId,
            @RequestBody ReportRequest.Create request) {

        reportService.createChatReport(reporterId, request.getChatId(), request.getReason());
        return ResponseEntity.ok("신고가 성공적으로 접수되었습니다.");
    }

    // [관리자] 신고 목록 조회
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    @Operation(summary = "[관리자] 신고 목록 조회", description = "접수된 신고 내역을 상태별로 조회합니다.")
    public ResponseEntity<Page<ReportResponse>> getReports(
            @RequestParam(required = false) ReportStatus status,
            Pageable pageable) {

        Page<Report> reportPage = reportService.getReportsByStatus(status, pageable);
        // Entity Page -> DTO Page 변환
        Page<ReportResponse> responsePage = reportPage.map(ReportResponse::from);

        return ResponseEntity.ok(responsePage);
    }

    // [관리자] 신고 처리 (승인/반려)
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{reportId}/status")
    @Operation(summary = "[관리자] 신고 처리", description = "신고 상태를 변경합니다 (ACCEPTED/REJECTED).")
    public ResponseEntity<String> processReport(
            @PathVariable Long reportId,
            @RequestBody ReportRequest.Process request) {

        reportService.processReport(reportId, request.getStatus());
        return ResponseEntity.ok("신고 상태가 변경되었습니다.");
    }

    // [관리자] 특정 유저 누적 신고 횟수 조회
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/count/{userId}")
    @Operation(summary = "[관리자] 유저 신고 누적 횟수 조회", description = "특정 유저가 신고당한 총 횟수를 조회합니다.")
    public ResponseEntity<Long> getReportCount(@PathVariable Long userId) {
        return ResponseEntity.ok(reportService.getReportCountForUser(userId));
    }
}
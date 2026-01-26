package com.ohgiraffers.backendapi.domain.report.dto;

import com.ohgiraffers.backendapi.domain.report.entity.Report;
import com.ohgiraffers.backendapi.domain.report.enums.ReportStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ReportResponse {

    private Long reportId;
    private Long reporterId;
    private String reporterName;
    private Long targetUserId;
    private String targetUserName;
    private String reason;
    private String reportedContent;
    private ReportStatus status;
    private LocalDateTime createdAt;

    // Entity -> DTO 변환 메서드
    public static ReportResponse from(Report report) {
        return ReportResponse.builder()
                .reportId(report.getId())
                .reporterId(report.getReporter().getId())
                .reporterName(report.getReporter().getLoginId()) // 또는 Nickname
                .targetUserId(report.getTargetUser().getId())
                .targetUserName(report.getTargetUser().getLoginId())
                .reason(report.getReason())
                .reportedContent(report.getReportedContent())
                .status(report.getStatus())
                .createdAt(report.getCreatedAt())
                .build();
    }
}
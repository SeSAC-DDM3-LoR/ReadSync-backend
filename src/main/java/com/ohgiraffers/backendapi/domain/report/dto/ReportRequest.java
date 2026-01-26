package com.ohgiraffers.backendapi.domain.report.dto;

import com.ohgiraffers.backendapi.domain.report.enums.ReportStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

public class ReportRequest {

    @Getter
    @Setter
    public static class Create {
        @NotNull(message = "채팅 ID는 필수입니다.")
        @Schema(description = "신고할 채팅 로그 ID", example = "10")
        private Long chatId;

        @NotBlank(message = "신고 사유는 필수입니다.")
        @Schema(description = "신고 사유", example = "욕설 및 비하 발언")
        private String reason;
    }

    @Getter
    @Setter
    public static class Process {
        @NotNull(message = "처리 상태는 필수입니다.")
        @Schema(description = "변경할 처리 상태 (ACCEPTED, REJECTED 등)", example = "ACCEPTED")
        private ReportStatus status;
    }
}
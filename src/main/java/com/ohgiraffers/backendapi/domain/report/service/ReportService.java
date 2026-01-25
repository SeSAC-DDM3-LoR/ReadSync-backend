package com.ohgiraffers.backendapi.domain.report.service;

import com.ohgiraffers.backendapi.domain.blacklist.service.BlacklistService;
import com.ohgiraffers.backendapi.domain.chat.entity.ChatLog;
import com.ohgiraffers.backendapi.domain.chat.repository.ChatLogRepository;
import com.ohgiraffers.backendapi.domain.report.entity.Report;
import com.ohgiraffers.backendapi.domain.report.enums.ReportStatus;
import com.ohgiraffers.backendapi.domain.report.repository.ReportRepository;
import com.ohgiraffers.backendapi.domain.user.entity.User;
import com.ohgiraffers.backendapi.domain.user.repository.UserRepository;
import com.ohgiraffers.backendapi.global.error.CustomException;
import com.ohgiraffers.backendapi.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final ReportRepository reportRepository;
    private final ChatLogRepository chatLogRepository;
    private final UserRepository userRepository;

    // [핵심] 블랙리스트 서비스 주입 (검증용)
    private final BlacklistService blacklistService;

    /**
     * [사용자] 채팅 신고 접수
     * - 1. 신고자가 차단된 상태인지 확인 (BlacklistService 활용)
     * - 2. 채팅 로그 존재 확인
     * - 3. 본인 신고 방지
     * - 4. 신고 저장 (증거 스냅샷 포함)
     */
    @Transactional
    public void createChatReport(Long reporterId, Long chatId, String reason) {
        // 1. 신고자 차단 여부 검증 (차단된 유저는 신고 불가)
        blacklistService.validateUserBanStatus(reporterId);

        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        ChatLog chatLog = chatLogRepository.findById(chatId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_NOT_FOUND));

        // 3. 본인 신고 방지
        if (chatLog.getUser().getId().equals(reporterId)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "본인의 채팅은 신고할 수 없습니다.");
        }

        // 4. 중복 신고 방지
        if (reportRepository.existsByReporterAndChatLog(reporter, chatLog)) {
            throw new CustomException(ErrorCode.DUPLICATE_REPORT);
        }

        // 4. 신고 저장
        Report report = Report.builder()
                .reporter(reporter)
                .targetUser(chatLog.getUser()) // 피신고자 설정
                .chatLog(chatLog)
                .reason(reason)
                .reportedContent(chatLog.getContent()) // 증거 보존
                .build();

        reportRepository.save(report);
    }

    /**
     * [관리자] 신고 목록 조회 (상태별 필터링 + 페이징)
     */
    public Page<Report> getReportsByStatus(ReportStatus status, Pageable pageable) {
        if (status == null) {
            return reportRepository.findAll(pageable);
        }
        return reportRepository.findByStatus(status, pageable);
    }

    /**
     * [관리자] 신고 처리 (승인 또는 반려)
     */
    @Transactional
    public void processReport(Long reportId, ReportStatus newStatus) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "신고 내역을 찾을 수 없습니다."));

        report.processReport(newStatus);
    }

    /**
     * [관리자] 특정 유저가 받은 신고 횟수 조회 (블랙리스트 선정 참고용)
     */
    public long getReportCountForUser(Long targetUserId) {
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return reportRepository.countByTargetUser(targetUser);
    }
}
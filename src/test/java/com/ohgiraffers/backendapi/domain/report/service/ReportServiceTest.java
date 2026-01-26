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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @InjectMocks
    private ReportService reportService;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ChatLogRepository chatLogRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BlacklistService blacklistService;

    @Test
    @DisplayName("채팅 신고 성공: 정상적인 요청일 경우 신고가 접수된다.")
    void createChatReport_Success() {
        // given
        Long reporterId = 1L;
        Long targetUserId = 2L;
        Long chatId = 10L;
        String reason = "욕설";

        User reporter = mock(User.class);
        User targetUser = mock(User.class);
        ChatLog chatLog = mock(ChatLog.class);

        // Mocking behavior
        willDoNothing().given(blacklistService).validateUserBanStatus(reporterId); // 차단되지 않음
        given(userRepository.findById(reporterId)).willReturn(Optional.of(reporter));
        given(chatLogRepository.findById(chatId)).willReturn(Optional.of(chatLog));
        given(chatLog.getUser()).willReturn(targetUser);
        given(targetUser.getId()).willReturn(targetUserId); // 본인 아님
        given(chatLog.getContent()).willReturn("문제의 발언"); // 스냅샷용

        // when
        reportService.createChatReport(reporterId, chatId, reason);

        // then
        verify(reportRepository, times(1)).save(any(Report.class));
    }

    @Test
    @DisplayName("채팅 신고 실패: 본인의 채팅을 신고할 수 없다.")
    void createChatReport_Fail_SelfReport() {
        // given
        Long reporterId = 1L;
        Long chatId = 10L;

        User reporter = mock(User.class);
        ChatLog chatLog = mock(ChatLog.class);
        User targetUser = mock(User.class); // 본인

        willDoNothing().given(blacklistService).validateUserBanStatus(reporterId);
        given(userRepository.findById(reporterId)).willReturn(Optional.of(reporter));
        given(chatLogRepository.findById(chatId)).willReturn(Optional.of(chatLog));
        given(chatLog.getUser()).willReturn(targetUser);
        given(targetUser.getId()).willReturn(reporterId); // ID가 같음 (본인)

        // when & then
        assertThatThrownBy(() -> reportService.createChatReport(reporterId, chatId, "이유"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("신고 처리: 관리자가 신고 상태를 변경한다.")
    void processReport_Success() {
        // given
        Long reportId = 100L;
        Report report = mock(Report.class);
        given(reportRepository.findById(reportId)).willReturn(Optional.of(report));

        // when
        reportService.processReport(reportId, ReportStatus.PROCESSED);

        // then
        verify(report, times(1)).processReport(ReportStatus.PROCESSED);
    }

    @Test
    @DisplayName("신고 목록 조회: 상태별로 페이징 조회된다.")
    void getReportsByStatus() {
        // given
        Pageable pageable = PageRequest.of(0, 10);
        ReportStatus status = ReportStatus.PENDING;
        Page<Report> emptyPage = new PageImpl<>(Collections.emptyList());

        given(reportRepository.findByStatus(status, pageable)).willReturn(emptyPage);

        // when
        Page<Report> result = reportService.getReportsByStatus(status, pageable);

        // then
        assertThat(result).isNotNull();
        verify(reportRepository, times(1)).findByStatus(status, pageable);
    }
}
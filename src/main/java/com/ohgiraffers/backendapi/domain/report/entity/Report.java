package com.ohgiraffers.backendapi.domain.report.entity;

import com.ohgiraffers.backendapi.domain.chat.entity.ChatLog;
import com.ohgiraffers.backendapi.domain.report.enums.ReportStatus;
import com.ohgiraffers.backendapi.domain.user.entity.User;
import com.ohgiraffers.backendapi.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "reports")
public class Report extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long id;

    // 신고자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    // [New] 피신고자 (반정규화: 조회 성능 향상)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id", nullable = false)
    private User targetUser;

    // 원본 채팅 (삭제될 수 있음 -> Nullable & Set Null)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = true) // 원본이 지워져도 신고 내역은 남아야 함
    @OnDelete(action = OnDeleteAction.SET_NULL) // DB 레벨에서 FK 제약 조건 처리
    private ChatLog chatLog;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    // [New] 증거 보존용 스냅샷 (원본 채팅이 삭제되어도 내용은 남음)
    @Column(name = "reported_content", nullable = false, columnDefinition = "TEXT")
    private String reportedContent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status = ReportStatus.PENDING;

    @Builder
    public Report(User reporter, User targetUser, ChatLog chatLog, String reason, String reportedContent) {
        this.reporter = reporter;
        this.targetUser = targetUser;
        this.chatLog = chatLog;
        this.reason = reason;
        this.reportedContent = reportedContent;
        this.status = ReportStatus.PENDING;
    }

    // 신고 처리 상태 변경 (접수 -> 처리중 -> 완료/반려)
    public void processReport(ReportStatus newStatus) {
        this.status = newStatus;
    }
}
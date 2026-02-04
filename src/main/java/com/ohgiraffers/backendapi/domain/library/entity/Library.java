package com.ohgiraffers.backendapi.domain.library.entity;

import com.ohgiraffers.backendapi.domain.book.entity.Book;
import com.ohgiraffers.backendapi.domain.library.enums.OwnershipType;
import com.ohgiraffers.backendapi.domain.library.enums.ReadingStatus;
import com.ohgiraffers.backendapi.domain.user.entity.User;
import com.ohgiraffers.backendapi.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Table(name = "libraries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class Library extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long libraryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private OwnershipType ownershipType;

    @Builder.Default
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal totalProgress = BigDecimal.ZERO;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReadingStatus readingStatus = ReadingStatus.BEFORE_READING; // 기본값: 읽기 전

    private LocalDateTime expiresAt; // 대여 시 만료일 (소유 시에는 매우 먼 미래 날짜 혹은 특정 규칙 적용)

    @Column(name = "total_read_paragraphs")
    private Integer totalReadParagraphs;

    @Column(name = "last_read_chapter_id")
    private Long lastReadChapterId;

    @Builder.Default
    @Column(name = "last_vector_update_step")
    private Integer lastVectorUpdateStep = 0; // 0, 30, 70, 100

    @Transient
    @Setter
    private int reachedMilestone = 0; // 이번 업데이트에서 달성한 마일스톤 (일시적)

    @Transient
    @Setter
    private float gainedWeight = 0.0f; // [New] 이번 업데이트에서 획득한 가중치 합계

    public void incrementReadCount(int count) {
        if (this.totalReadParagraphs == null) {
            this.totalReadParagraphs = 0;
        }
        this.totalReadParagraphs += count;
    }

    // 독서 상태 및 만료일 수정 로직
    public void updateStatus(ReadingStatus readingStatus) {
        this.readingStatus = readingStatus;
    }

    public void updateOverallProgress(double overallProgress) {

        // new BigDecimal(double) 보다는 BigDecimal.valueOf(double)이 정밀도 면에서 훨씬 안전합니다.
        this.totalProgress = BigDecimal.valueOf(overallProgress)
                .setScale(2, RoundingMode.HALF_UP); // 소수점 2자리 반올림
    }

    public void updateLastReadChapter(Long chapterId) {
        this.lastReadChapterId = chapterId;
    }

    public void updateVectorUpdateStep(int step) {
        this.lastVectorUpdateStep = step;
    }
}

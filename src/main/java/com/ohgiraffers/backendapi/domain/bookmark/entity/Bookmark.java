package com.ohgiraffers.backendapi.domain.bookmark.entity;

import com.ohgiraffers.backendapi.domain.chapter.entity.Chapter;
import com.ohgiraffers.backendapi.domain.library.entity.Library;
import com.ohgiraffers.backendapi.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.type.descriptor.jdbc.BinaryJdbcType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Entity
@Table(name = "bookmarks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Bookmark extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bookmarkId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id", nullable = false)
    private Chapter chapter;

    @Column(nullable = false)
    private Integer lastReadPos;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal progress;

    // @Lob // <--- 이 부분을 삭제하거나 주석 처리하세요!
    @JdbcType(BinaryJdbcType.class) // JDBC 타입을 명시적으로 이진 데이터로 지정
    @Column(nullable = false)
    private byte[] readMask;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "library_id", nullable = false)
    private Library library;

    public int syncReadStatus(List<Integer> newIndices, Integer lastReadPos) {

        String currentMask = new String(this.readMask, StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(currentMask);

        int newlyReadCount = 0; // 이번 호출에서 처음 읽게 된 문단 수

        for (Integer index : newIndices) {
            // 범위를 벗어난 인덱스는 무시 (1 ~ paragraphs 범위만 허용)
            if (index < 1 || index > chapter.getParagraphs()) {
                continue;
            }

            int setIndex = index - 1;
            // 기존에 '0'이었을 때만 '1'로 바꾸고 카운트를 올립니다 (중복 반영 방지)
            if (sb.charAt(setIndex) == '0') {
                sb.setCharAt(setIndex, '1');
                newlyReadCount++;
            }
        }

        // String updatedMask = getString(newIndices);
        this.readMask = sb.toString().getBytes(StandardCharsets.UTF_8);
        this.lastReadPos = lastReadPos;

        // 2. 진행률 계산
        long readCount = sb.chars().filter(ch -> ch == '1').count();
        this.progress = BigDecimal.valueOf(readCount)
                .divide(BigDecimal.valueOf(chapter.getParagraphs()), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        return newlyReadCount;
    }

    private String getString(List<Integer> newIndices) {
        StringBuilder sb = new StringBuilder(new String(this.readMask, StandardCharsets.UTF_8));

        // 1. 마스크 업데이트
        for (Integer index : newIndices) {
            // 1. 유효성 검사: 1보다 작거나, 최대값(totalLength)보다 크면 예외 발생
            if (index < 1 || index > chapter.getParagraphs()) {
                throw new IllegalArgumentException(
                        String.format("잘못된 문단 번호입니다: %d (허용 범위: 1 ~ %d)", index, chapter.getParagraphs()));
            }

            // 2. 인덱스 보정: 사용자가 입력한 1~10을 0~9로 변환
            int setIndex = index - 1;
            sb.setCharAt(setIndex, '1');
        }

        return sb.toString();
    }
}
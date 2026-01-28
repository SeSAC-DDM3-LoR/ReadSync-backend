package com.ohgiraffers.backendapi.domain.aichat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RAG 출처 정보 DTO
 * AI가 답변을 생성할 때 참조한 책 내용의 출처 정보입니다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceReferenceDTO {

    private List<String> paragraphIds; // 출처 문단 ID 목록
    private String contentPreview; // 출처 내용 미리보기 (200자 정도)
    private Double similarity; // 유사도 점수 (0~1)
}

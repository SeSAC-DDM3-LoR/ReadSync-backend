package com.ohgiraffers.backendapi.domain.reading.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class ReadingPulseRequestDTO {
    private Long libraryId;
    private Long chapterId;
    private Integer lastReadPos; // 추가
    private List<Integer> readParagraphIndices; // 추가 (개수 대신 리스트)
    private int readTime;
}

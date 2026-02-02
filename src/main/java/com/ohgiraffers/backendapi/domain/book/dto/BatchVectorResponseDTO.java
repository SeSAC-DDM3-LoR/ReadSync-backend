package com.ohgiraffers.backendapi.domain.book.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchVectorResponseDTO {

    // 파이썬의 "book_vector" 키와 매핑
    @JsonProperty("book_vector")
    private float[] bookVector;

    // 파이썬의 "chapter_vectors" 키와 매핑
    // 리스트 안에 각 챕터의 float 배열이 들어있는 구조입니다.
    @JsonProperty("chapter_vectors")
    private List<float[]> chapterVectors;
}

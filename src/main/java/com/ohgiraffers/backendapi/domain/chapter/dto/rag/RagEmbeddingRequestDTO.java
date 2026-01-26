package com.ohgiraffers.backendapi.domain.chapter.dto.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagEmbeddingRequestDTO {
    private List<Map<String, Object>> content;
}

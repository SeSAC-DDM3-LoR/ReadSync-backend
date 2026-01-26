package com.ohgiraffers.backendapi.domain.chapter.dto.rag;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RagEmbeddingResponseDTO {

    private List<ChunkEmbeddingDTO> embeddings;

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class ChunkEmbeddingDTO {
        @JsonProperty("content_chunk")
        private String contentChunk;

        @JsonProperty("chunk_index")
        private Integer chunkIndex;

        private float[] vector;

        @JsonProperty("paragraph_ids")
        private List<String> paragraphIds;
    }
}

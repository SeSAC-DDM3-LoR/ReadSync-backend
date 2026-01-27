package com.ohgiraffers.backendapi.domain.chapter.dto.rag;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class RagEmbeddingResponseDTO {

    @JsonProperty("parents")
    private List<ParentChunkDTO> parents;

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    public static class ParentChunkDTO {
        @JsonProperty("content_text")
        private String contentText;

        @JsonProperty("speaker_list")
        private List<String> speakerList;

        @JsonProperty("paragraph_ids")
        private List<String> paragraphIds;

        @JsonProperty("start_paragraph_id")
        private String startParagraphId;

        @JsonProperty("end_paragraph_id")
        private String endParagraphId;

        private List<ChildChunkDTO> children;
    }

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    public static class ChildChunkDTO {
        @JsonProperty("content_text")
        private String contentText;

        private List<Float> vector;

        @JsonProperty("chunk_index")
        private Integer chunkIndex;

        @JsonProperty("paragraph_ids")
        private List<String> paragraphIds;
    }
}

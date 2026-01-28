package com.ohgiraffers.backendapi.domain.chapter.dto.rag;

import com.ohgiraffers.backendapi.domain.chapter.entity.RagParentDocument;
import lombok.*;

import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RagSearchResponseDTO {

    private Long parentId;
    private Long chapterId;
    private String contentText;
    private List<String> speakerList;
    private List<String> paragraphIds;
    private String startParagraphId;
    private String endParagraphId;
    private Double similarity; // [Similarrity Score] Added

    // Entity -> DTO (기존 호환성 유지)
    public static RagSearchResponseDTO from(RagParentDocument entity) {
        return RagSearchResponseDTO.builder()
                .parentId(entity.getId())
                .chapterId(entity.getChapterId())
                .contentText(entity.getContentText())
                .speakerList(entity.getSpeakerList())
                .paragraphIds(entity.getParagraphIds())
                .startParagraphId(entity.getStartParagraphId())
                .endParagraphId(entity.getEndParagraphId())
                .build();
    }

    // Projection -> DTO (유사도 포함)
    public static RagSearchResponseDTO from(
            com.ohgiraffers.backendapi.domain.chapter.repository.RagSearchResultProjection projection) {
        return RagSearchResponseDTO.builder()
                .parentId(projection.getParentId())
                .chapterId(projection.getChapterId())
                .contentText(projection.getContentText())
                .speakerList(projection.getSpeakerList())
                .paragraphIds(projection.getParagraphIds())
                .startParagraphId(projection.getStartParagraphId())
                .endParagraphId(projection.getEndParagraphId())
                .similarity(projection.getSimilarity())
                .build();
    }
}

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
}

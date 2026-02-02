package com.ohgiraffers.backendapi.domain.chapter.dto.rag;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RagStatusResponseDTO {
    private Long chapterId;
    private long parentDocumentCount;
    private long childVectorCount;
    private boolean isEmbedded;
}

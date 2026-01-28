package com.ohgiraffers.backendapi.domain.chapter.repository;

import java.util.List;

public interface RagSearchResultProjection {
    Long getParentId();

    Long getChapterId();

    String getContentText();

    List<String> getSpeakerList();

    List<String> getParagraphIds();

    String getStartParagraphId();

    String getEndParagraphId();

    Double getSimilarity();
}

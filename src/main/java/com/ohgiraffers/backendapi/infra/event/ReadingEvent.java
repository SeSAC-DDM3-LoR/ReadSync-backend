package com.ohgiraffers.backendapi.infra.event;

import com.ohgiraffers.backendapi.domain.reading.dto.ReadingPulseRequestDTO;
import lombok.Getter;

import java.util.List;

@Getter
public class ReadingEvent {
    private final Long userId;
    private final Long libraryId;
    private final Long chapterId;
    private final Integer lastReadPos;
    private final List<Integer> readParagraphIndices;
    private final int readTime;

    public ReadingEvent(Long userId, ReadingPulseRequestDTO request) {
        this.userId = userId;
        this.libraryId = request.getLibraryId();
        this.chapterId = request.getChapterId();
        this.lastReadPos = request.getLastReadPos();
        this.readParagraphIndices = request.getReadParagraphIndices();
        this.readTime = request.getReadTime();
    }
}

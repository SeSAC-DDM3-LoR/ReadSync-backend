package com.ohgiraffers.backendapi.domain.readingroom.event;

/**
 * 독서룸 종료 이벤트
 * 
 * @param roomId 종료된 방의 ID
 */
public record RoomFinishedEvent(Long roomId) {
}

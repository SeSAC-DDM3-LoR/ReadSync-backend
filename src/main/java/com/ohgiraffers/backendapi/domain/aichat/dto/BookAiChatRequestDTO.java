package com.ohgiraffers.backendapi.domain.aichat.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI 채팅 메시지 전송 요청 DTO
 * chatType은 AI가 자동으로 분류합니다.
 */
@Getter
@NoArgsConstructor
public class BookAiChatRequestDTO {

    private String userMessage; // 필수: 사용자 질문
    private String currentParagraphId; // 선택: 사용자가 현재 읽고 있는 마지막 문단 ID
}

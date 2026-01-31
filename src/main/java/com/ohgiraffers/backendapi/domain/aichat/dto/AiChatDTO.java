package com.ohgiraffers.backendapi.domain.aichat.dto;

import com.ohgiraffers.backendapi.domain.aichat.enums.ChatType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class AiChatDTO {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {
        private String userMsg;
        private ChatType chatType; // Optional: 사용자가 명시하지 않으면 AI가 판단
        private String currentParagraphId; // 문맥 파악용 문단 ID
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long chatId;
        private Long roomId;
        private ChatType chatType;
        private String userMsg;
        private String aiMsg;
        private Integer tokenCount;
        private Integer responseTimeMs;
        private Integer rating;
        private LocalDateTime createdAt;
        private String relatedParagraphId; // RAG로 참조한 문단 ID (선택)
    }

    // Python AI 서버 요청/응답용 DTO
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AiClassifyRequest {
        private String user_msg;
        private Long chapter_id;
        private String current_paragraph_content;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiClassifyResponse {
        private String chat_type;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AiGenerateRequest {
        private String user_msg;
        private String chat_type;
        private String rag_context;
        // previous_messages: [{"role": "user", "content": "..."}, {"role": "assistant",
        // "content": "..."}]
        private java.util.List<java.util.Map<String, String>> previous_messages;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiGenerateResponse {
        private String response;
        private Integer token_usage;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AiRewriteRequest {
        private String user_msg;
        private java.util.List<java.util.Map<String, String>> previous_messages;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiRewriteResponse {
        private String rewritten_query;
    }
}

package com.ohgiraffers.backendapi.global.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * ReadSync-ai TTS 서버와 통신하는 클라이언트
 */
@Slf4j
@Component
public class TtsClient {

        private final WebClient webClient;

        public TtsClient(@Value("${ai.server.url:http://localhost:8000}") String aiServerUrl) {
                this.webClient = WebClient.builder()
                                .baseUrl(aiServerUrl)
                                .build();
        }

        /**
         * AI 서버에서 TTS 오디오 URL 가져오기
         * 
         * @param chapterId   챕터 ID
         * @param paragraphId 문단 ID
         * @param voiceId     Luxia Voice ID
         * @param text        TTS 변환할 텍스트 내용
         * @return S3 presigned URL
         */
        public Mono<String> getAudioUrl(String chapterId, String paragraphId, int voiceId, String text) {
                log.info("Requesting TTS audio URL from AI server: pId={}, voice={}, textLen={}",
                                paragraphId, voiceId, text != null ? text.length() : 0);

                // Request Body 객체 생성 (chapter_id 포함 - 캐시 키 구분용)
                java.util.Map<String, Object> requestBody = new java.util.HashMap<>();
                requestBody.put("text", text);
                requestBody.put("voice_id", voiceId);
                requestBody.put("chapter_id", chapterId);

                return webClient.post()
                                .uri(uriBuilder -> uriBuilder
                                                .path("/api/v1/soy-test/play/{paragraphId}")
                                                .build(paragraphId))
                                .bodyValue(requestBody)
                                .retrieve()
                                .bodyToMono(TtsResponse.class)
                                .map(response -> response.url())
                                .doOnSuccess(url -> log.info("Received audio URL: {}", url))
                                .doOnError(error -> log.error("Failed to get audio URL from AI server", error));
        }

        /**
         * TTS API 응답 DTO
         */
        public record TtsResponse(String url, String paragraph_id) {
        }
}

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
     * @param chapterId   챕터 ID (예: "ch4") - 현재는 사용하지 않음
     * @param paragraphId 문단 ID (예: "p1")
     * @param voiceId     Luxia Voice ID (예: 76, 2, 5, 7, 8)
     * @return S3 presigned URL
     */
    public Mono<String> getAudioUrl(String chapterId, String paragraphId, int voiceId) {
        log.info("Requesting TTS audio URL from AI server: chapterId={}, paragraphId={}, voiceId={}", 
                chapterId, paragraphId, voiceId);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/soy-test/play/{paragraphId}")
                        .queryParam("voice_id", voiceId)
                        .build(paragraphId))
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

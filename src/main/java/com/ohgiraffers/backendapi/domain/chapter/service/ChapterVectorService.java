package com.ohgiraffers.backendapi.domain.chapter.service;

import com.ohgiraffers.backendapi.domain.chapter.dto.ChapterVectorResponseDTO;
import com.ohgiraffers.backendapi.domain.chapter.entity.Chapter;
import com.ohgiraffers.backendapi.domain.chapter.entity.ChapterVector;
import com.ohgiraffers.backendapi.domain.chapter.repository.ChapterRepository;
import com.ohgiraffers.backendapi.domain.chapter.repository.ChapterVectorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterVectorService {

    private final ChapterVectorRepository chapterVectorRepository;
    private final ChapterRepository chapterRepository;
    private final WebClient embeddingServerWebClient;

    /**
     * [복구] S3 URL을 통한 벡터 임베딩 호출
     */
    public float[] getVectorS3(String s3Url) {
        return embeddingServerWebClient.post()
                .uri("/api/v1/embed-from-s3")
                .bodyValue(Map.of("s3Url", s3Url))
                .retrieve()
                .bodyToMono(ChapterVectorResponseDTO.class)
                .map(ChapterVectorResponseDTO::getEmbedding)
                // 1. 개별 시도는 1분씩 (부팅 중엔 응답이 없을 수 있으니까요)
                .timeout(Duration.ofSeconds(60))
                // 2. 서버가 일어날 때까지 30초 간격으로 최대 5번만 다시 물어보기
                .retryWhen(Retry.fixedDelay(5, Duration.ofSeconds(30))
                        .doBeforeRetry(retrySignal -> log.warn("💤 서버 깨우는 중... (시도: {})", retrySignal.totalRetries() + 1)))
                // 3. 전체적으로 최대 5분까지는 기다려주기
                .block(Duration.ofMinutes(5));
    }

    /**
     * [유지] Google Drive URL을 통한 벡터 임베딩 호출
     */
    public float[] getVectorGD(String googleDriveUrl) {
        return embeddingServerWebClient.post()
                .uri("/api/v1/embed-from-drive")
                .bodyValue(Map.of("google_drive_url", googleDriveUrl))
                .retrieve()
                .bodyToMono(ChapterVectorResponseDTO.class)
                .map(ChapterVectorResponseDTO::getEmbedding)
                .timeout(Duration.ofSeconds(60))
                // 2. 서버가 일어날 때까지 30초 간격으로 최대 5번만 다시 물어보기
                .retryWhen(Retry.fixedDelay(5, Duration.ofSeconds(30))
                        .doBeforeRetry(retrySignal -> log.warn("💤 서버 깨우는 중... (시도: {})", retrySignal.totalRetries() + 1)))
                // 3. 전체적으로 최대 5분까지는 기다려주기
                .block(Duration.ofMinutes(5));
    }

    /**
     * [개선] 비동기 임베딩 작업 (S3 우선 순위)
     * 외부 서버 호출 시 DB 커넥션을 오래 잡지 않도록 로직을 분리했습니다.
     */
    @Transactional
    @Async
    public void saveOrUpdateChapterVector(Long chapterId) {
        // 1. 조회는 트랜잭션 없이 진행하여 부하 감소
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterId));

        try {
            log.info("🚀 S3 임베딩 시작 - Chapter ID: {}", chapterId);

            // 2. 외부 AI 서버 호출 (트랜잭션 밖에서 수행)
            // S3 URL이 있다면 S3로, 없다면 GD로 시도하도록 유연하게 짰습니다.
            float[] vectorResponse;
            if (chapter.getBookContentPath() != null && chapter.getBookContentPath().contains("s3")) {
                vectorResponse = getVectorS3(chapter.getBookContentPath());
            } else {
                vectorResponse = getVectorGD(chapter.getBookContentPath());
            }

            // 3. 실제 저장은 별도 트랜잭션에서 수행 (Atomic Update)
            saveToDatabase(chapter, vectorResponse);

            log.info("✅ 임베딩 완료 및 저장 성공 - Chapter ID: {}", chapterId);
        } catch (Exception e) {
            log.error("❌ 비동기 임베딩 중 오류 발생 - ID: {}, 사유: {}", chapterId, e.getMessage());
        }
    }

    /**
     * DB 저장 로직만 트랜잭션으로 묶어 효율을 높였습니다. (Upsert)
     */
    @Transactional
    public void saveToDatabase(Chapter chapter, float[] vectorResponse) {
        ChapterVector chapterVector = chapterVectorRepository.findById(chapter.getChapterId())
                .map(existing -> {
                    existing.updateVector(vectorResponse);
                    return existing;
                })
                .orElseGet(() -> ChapterVector.builder()
                        .chapter(chapter)
                        .vector(vectorResponse)
                        .build());

        chapterVectorRepository.save(chapterVector);
    }

    @Transactional(readOnly = true)
    public List<float[]> getChapterVectorsForBook(Long bookId) {
        return chapterVectorRepository.findAllVectorsByBookId(bookId);
    }
}
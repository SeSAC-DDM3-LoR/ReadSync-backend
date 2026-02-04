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
                .timeout(Duration.ofMinutes(4)) // 1. 여기서 넉넉히 기다려주고
                .block(); // 2. 여기서는 시간 제한 없이(혹은 5분 정도) 결과가 올 때까지 대기
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
                .timeout(Duration.ofMinutes(4)) // 1. 여기서 넉넉히 기다려주고
                .block(); // 2. 여기서는 시간 제한 없이(혹은 5분 정도) 결과가 올 때까지 대기
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

            String path = chapter.getBookContentPath();

            if (path != null && (path.contains("amazonaws.com") || path.contains(".s3."))) {
                vectorResponse = getVectorS3(path);
            } else if (path != null && path.contains("drive.google.com")) {
                vectorResponse = getVectorGD(path);
            } else {
                // 예외 처리 혹은 기본값
                throw new IllegalArgumentException("지원하지 않는 파일 경로 형식입니다: " + path);
            }

            // 3. 실제 저장은 별도 트랜잭션에서 수행 (Atomic Update)
            saveOrUpdateChapterVector(chapter, vectorResponse);

            log.info("✅ 임베딩 완료 및 저장 성공 - Chapter ID: {}", chapterId);
        } catch (Exception e) {
            log.error("❌ 비동기 임베딩 중 오류 발생 - ID: {}, 사유: {}", chapterId, e.getMessage());
        }
    }

    /**
     * DB 저장 로직만 트랜잭션으로 묶어 효율을 높였습니다. (Upsert)
     * [수정] Chapter 엔티티 대신 ID를 받아 내부에서 트랜잭션 내 조회를 수행합니다.
     */
    @Transactional
    public void saveVectorForChapter(Long chapterId, float[] vectorResponse) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found for saving vector: " + chapterId));

        ChapterVector chapterVector = chapterVectorRepository.findById(chapterId)
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

    /**
     * (Deprecated) 기존 메서드 유지 (하위 호환성)
     */
    public void saveOrUpdateChapterVector(Chapter chapter, float[] vectorResponse) {
        saveVectorForChapter(chapter.getChapterId(), vectorResponse);
    }

}
package com.ohgiraffers.backendapi.domain.chapter.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ohgiraffers.backendapi.domain.chapter.dto.rag.RagEmbeddingRequestDTO;
import com.ohgiraffers.backendapi.domain.chapter.dto.rag.RagEmbeddingResponseDTO;
import com.ohgiraffers.backendapi.domain.chapter.entity.Chapter;
import com.ohgiraffers.backendapi.domain.chapter.entity.ChapterVectorRag;
import com.ohgiraffers.backendapi.domain.chapter.repository.ChapterRepository;
import com.ohgiraffers.backendapi.domain.chapter.repository.ChapterVectorRagRepository;
import com.ohgiraffers.backendapi.global.error.CustomException;
import com.ohgiraffers.backendapi.global.error.ErrorCode;
import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterVectorRagService {

    private final ChapterVectorRagRepository chapterVectorRagRepository;
    private final ChapterRepository chapterRepository;
    private final WebClient embeddingServerWebClient;
    private final S3Template s3Template;
    private final ObjectMapper objectMapper;

    /**
     * [RAG] S3 파일 다운로드 -> Python AI 서버로 Content 전송 -> 임베딩 결과 DB 저장
     */
    @Async
    @Transactional
    public void processRagEmbedding(Long chapterId) {
        // 1. 챕터 정보 조회 (트랜잭션 내)
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAPTER_NOT_FOUND));

        String s3Url = chapter.getBookContentPath();
        if (s3Url == null) {
            log.warn("⚠️ URL이 존재하지 않아 RAG 임베딩을 건너뜁니다. Chapter ID: {}", chapterId);
            return;
        }

        try {
            log.info("🚀 RAG 임베딩 프로세스 시작 - Chapter ID: {}", chapterId);

            List<Map<String, Object>> contentList;

            // 2. 파일 다운로드 및 파싱 (S3 또는 Google Drive)
            if (s3Url.contains("google")) {
                contentList = downloadAndParseJsonFromGoogleDrive(s3Url);
            } else if (s3Url.contains("s3")) {
                contentList = downloadAndParseJsonFromS3(s3Url);
            } else {
                log.warn("⚠️ 지원하지 않는 URL 형식입니다. RAG 임베딩 취소. URL: {}", s3Url);
                return;
            }

            // 3. Python AI 서버 호출
            RagEmbeddingResponseDTO response = callEmbeddingServer(contentList);

            // 4. 결과 DB 저장 (기존 데이터 삭제 후 재저장 또는 업서트 전략)
            saveEmbeddingsToDatabase(chapter, response);

            log.info("✅ RAG 임베딩 완료 - Chapter ID: {}, Chunk Count: {}",
                    chapterId, response.getEmbeddings().size());

        } catch (Exception e) {
            log.error("❌ RAG 임베딩 처리 중 오류 발생 - ID: {}, 사유: {}", chapterId, e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> downloadAndParseJsonFromGoogleDrive(String driveUrl) {
        String fileId = null;

        // 1. /d/xxxx 패턴 시도
        java.util.regex.Pattern patternPath = java.util.regex.Pattern.compile("/d/([^/]+)");
        java.util.regex.Matcher matcherPath = patternPath.matcher(driveUrl);
        if (matcherPath.find()) {
            fileId = matcherPath.group(1);
        } else {
            // 2. id=xxxx 패턴 시도
            java.util.regex.Pattern patternQuery = java.util.regex.Pattern.compile("id=([^&]+)");
            java.util.regex.Matcher matcherQuery = patternQuery.matcher(driveUrl);
            if (matcherQuery.find()) {
                fileId = matcherQuery.group(1);
            }
        }

        if (fileId == null) {
            throw new CustomException(ErrorCode.RAG_INVALID_DRIVE_LINK);
        }

        String downloadUrl = "https://drive.google.com/uc?export=download&id=" + fileId;

        try {
            // WebClient 대신 java.net.URL을 사용하여 리디렉션 처리 및 Content-Type 무시 (JSON 파싱 강제)
            java.net.URL url = new java.net.URI(downloadUrl).toURL();
            try (InputStream inputStream = url.openStream()) {
                Map<String, Object> bookData = objectMapper.readValue(inputStream, new TypeReference<>() {
                });

                if (bookData == null || !bookData.containsKey("content")) {
                    throw new CustomException(ErrorCode.RAG_CONTENT_NOT_FOUND);
                }

                return (List<Map<String, Object>>) bookData.get("content");
            }
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google Drive download error parsing: {}", e.getMessage());
            throw new CustomException(ErrorCode.RAG_CONTENT_PARSE_ERROR);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> downloadAndParseJsonFromS3(String s3Url) throws IOException, URISyntaxException {
        // s3://bucket/key 또는 https://... 형태 처리
        URI uri = new URI(s3Url);
        String bucket;
        String key;

        if ("s3".equalsIgnoreCase(uri.getScheme())) {
            bucket = uri.getHost();
            key = uri.getPath().substring(1); // remove leading slash
        } else {
            // Assuming format like https://bucket.s3.region.amazonaws.com/key or similar
            // This parsing depends heavily on URL format.
            // Simple fallback: if stored as just path or specific convention, adjust here.
            // Given user said "actual AWS S3 URL", let's try to extract bucket/key.

            // For robust S3Template usage, we normally need bucket and key.
            // Let's assume standard URL structure or that we can pass the logic handled by
            // AWS SDK if we use S3Resource.
            // But S3Template methods usually take bucket and key.

            // If the URL is full HTTP URL, parsing might be complex.
            // Let's assume standard parsing logic or try to read as a Resource if
            // S3Template supports S3ProtocolResolver.
            // However, for S3Template.download, we need bucket & key.

            // IMPORTANT: User's logic in Python was:
            // bucket_name = parsed_url.netloc.split('.')[0]
            // key = parsed_url.path.lstrip('/')
            // We can mimic this if the hostname is bucket.s3...

            String host = uri.getHost();
            if (host != null && host.contains(".s3")) {
                bucket = host.split("\\.")[0];
            } else {
                // fallback or specific logic needed
                // If using 's3://' style stored in DB, scheme is s3.
                // If DB has http link, we try to parse.
                // If it fails, we might just try to use RestTemplate to download if public?
                // But user implied secured S3.
                throw new CustomException(ErrorCode.RAG_UNSUPPORTED_URL);
            }
            key = uri.getPath().substring(1);
        }

        // Read file content
        try (InputStream inputStream = s3Template.download(bucket, key).getInputStream()) {
            Map<String, Object> bookData = objectMapper.readValue(inputStream, new TypeReference<>() {
            });
            // 'content' 필드 추출
            return (List<Map<String, Object>>) bookData.get("content");
        }
    }

    private RagEmbeddingResponseDTO callEmbeddingServer(List<Map<String, Object>> contentList) {
        return embeddingServerWebClient.post()
                .uri("/api/v1/embed-rag-content")
                .bodyValue(new RagEmbeddingRequestDTO(contentList))
                .retrieve()
                .bodyToMono(RagEmbeddingResponseDTO.class)
                .timeout(Duration.ofMinutes(5)) // 긴 텍스트 처리 시간 고려
                .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(10)))
                .block(Duration.ofMinutes(10));
    }

    @Transactional
    protected void saveEmbeddingsToDatabase(Chapter chapter, RagEmbeddingResponseDTO response) {
        // 기존 RAG 데이터 정리 (재임베딩 시)
        chapterVectorRagRepository.deleteByChapter_ChapterId(chapter.getChapterId());

        List<ChapterVectorRag> entities = response.getEmbeddings().stream()
                .map(dto -> ChapterVectorRag.builder()
                        .chapter(chapter)
                        .contentChunk(dto.getContentChunk())
                        .chunkIndex(dto.getChunkIndex())
                        .vector(dto.getVector())
                        .paragraphIds(dto.getParagraphIds())
                        .build())
                .collect(Collectors.toList());

        chapterVectorRagRepository.saveAll(entities);
    }
}

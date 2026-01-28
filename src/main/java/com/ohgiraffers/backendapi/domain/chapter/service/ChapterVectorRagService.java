package com.ohgiraffers.backendapi.domain.chapter.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ohgiraffers.backendapi.domain.chapter.dto.rag.RagEmbeddingRequestDTO;
import com.ohgiraffers.backendapi.domain.chapter.dto.rag.RagEmbeddingResponseDTO;
import com.ohgiraffers.backendapi.domain.chapter.entity.Chapter;
import com.ohgiraffers.backendapi.domain.chapter.entity.RagChildVector;
import com.ohgiraffers.backendapi.domain.chapter.entity.RagParentDocument;
import com.ohgiraffers.backendapi.domain.chapter.repository.ChapterRepository;
import com.ohgiraffers.backendapi.domain.chapter.repository.RagChildRepository;
import com.ohgiraffers.backendapi.domain.chapter.repository.RagParentRepository;
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

    private final RagParentRepository ragParentRepository;
    private final RagChildRepository ragChildRepository;
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

            // 4. 결과 DB 저장 (기존 데이터 삭제 후 재저장)
            saveEmbeddingsToDatabase(chapter, response);

            int parentCount = (response.getParents() != null) ? response.getParents().size() : 0;
            log.info("✅ RAG 임베딩 완료 - Chapter ID: {}, Parent Count: {}", chapterId, parentCount);

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
            String host = uri.getHost();
            if (host != null && host.contains(".s3")) {
                bucket = host.split("\\.")[0];
            } else {
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
        log.info("📤 Python 서버로 {} 개의 콘텐츠 노드를 전송합니다.", contentList.size());
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
        // 1. 기존 RAG 데이터 정리 (재임베딩 시)
        List<RagParentDocument> existingParents = ragParentRepository.findByChapterId(chapter.getChapterId());

        if (!existingParents.isEmpty()) {
            // Child 먼저 삭제 (FK 제약조건 때문 - 명시적 삭제가 안전함)
            ragChildRepository.deleteByParentIn(existingParents);
            // Parent 삭제
            ragParentRepository.deleteAll(existingParents);
        }

        // 2. 신규 데이터 저장
        if (response.getParents() == null || response.getParents().isEmpty()) {
            log.warn("임베딩 결과가 없습니다. Chapter ID: {}", chapter.getChapterId());
            return;
        }

        for (RagEmbeddingResponseDTO.ParentChunkDTO parentDto : response.getParents()) {
            RagParentDocument parent = RagParentDocument.builder()
                    .chapterId(chapter.getChapterId())
                    .contentText(parentDto.getContentText())
                    .speakerList(parentDto.getSpeakerList())
                    .paragraphIds(parentDto.getParagraphIds())
                    .startParagraphId(parentDto.getStartParagraphId())
                    .endParagraphId(parentDto.getEndParagraphId())
                    .build();

            RagParentDocument savedParent = ragParentRepository.save(parent);

            if (parentDto.getChildren() != null) {
                List<RagChildVector> children = parentDto.getChildren().stream()
                        .map(childDto -> RagChildVector.builder()
                                .parent(savedParent) // 연관관계 설정
                                .contentText(childDto.getContentText())
                                .vector(childDto.getVector())
                                .chunkIndex(childDto.getChunkIndex())
                                .paragraphIds(childDto.getParagraphIds())
                                .build())
                        .collect(Collectors.toList());

                ragChildRepository.saveAll(children);
            }
        }

        // 3. Chapter 상태 업데이트 (임베딩 완료)
        chapter.markAsEmbedded();
        chapterRepository.save(chapter);
        log.info("✅ Chapter ID: {} 상태 업데이트 완료 (isEmbedded = true)", chapter.getChapterId());
    }

    @Transactional(readOnly = true)
    public List<com.ohgiraffers.backendapi.domain.chapter.dto.rag.RagSearchResponseDTO> searchRag(Long chapterId,
            String query) {
        // 1. Query Vectorization
        List<Float> queryVector = callEmbeddingQueryServer(query);

        // 2. Search DB (convert List<Float> to String for native query)
        String vectorString = queryVector.toString();
        List<com.ohgiraffers.backendapi.domain.chapter.repository.RagSearchResultProjection> results = ragChildRepository
                .findTop5ByVectorSimilarity(chapterId, vectorString);

        // 3. Map to DTO (유사도 포함)
        // Note: 이미 Repository에서 상위 5개를 가져왔으므로, 그대로 반환하거나
        // ParentId 기준으로 중복을 제거하고 싶다면 첫 번째(가장 높은 유사도)만 남기는 로직 추가 가능.
        // 여기서는 단순 변환만 수행 (Projection -> DTO)
        return results.stream()
                .map(com.ohgiraffers.backendapi.domain.chapter.dto.rag.RagSearchResponseDTO::from)
                .collect(Collectors.toList());
    }

    private List<Float> callEmbeddingQueryServer(String text) {
        Map<String, String> request = java.util.Collections.singletonMap("text", text);
        // Response format: { "embedding": [ ... ] }
        Map<String, List<Float>> response = embeddingServerWebClient.post()
                .uri("/api/v1/embed-query")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, List<Float>>>() {
                })
                .block(Duration.ofSeconds(30));

        if (response == null || !response.containsKey("embedding")) {
            throw new CustomException(ErrorCode.RAG_EMBEDDING_FAILED);
        }
        return response.get("embedding");
    }
}

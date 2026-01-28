package com.ohgiraffers.backendapi.domain.book.service;

import com.ohgiraffers.backendapi.domain.book.dto.BatchVectorResponseDTO;
import com.ohgiraffers.backendapi.domain.book.dto.BookRecommendationDTO;
import com.ohgiraffers.backendapi.domain.book.dto.BookResponseDTO;
import com.ohgiraffers.backendapi.domain.book.dto.BookVectorDTO;
import com.ohgiraffers.backendapi.domain.book.entity.Book;
import com.ohgiraffers.backendapi.domain.book.entity.BookVector;
import com.ohgiraffers.backendapi.domain.book.repository.BookRepository;
import com.ohgiraffers.backendapi.domain.book.repository.BookVectorRepository;
import com.ohgiraffers.backendapi.domain.chapter.entity.Chapter;
import com.ohgiraffers.backendapi.domain.chapter.entity.ChapterVector;
import com.ohgiraffers.backendapi.domain.chapter.repository.ChapterRepository;
import com.ohgiraffers.backendapi.domain.chapter.repository.ChapterVectorRepository;
import com.ohgiraffers.backendapi.domain.chapter.service.ChapterVectorService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookVectorService {

    private final BookVectorRepository bookVectorRepository;
    private final BookRepository bookRepository;
    private final ChapterRepository chapterRepository;
    private final ChapterVectorRepository chapterVectorRepository;
    private final WebClient embeddingServerWebClient;
    private final ChapterVectorService chapterVectorService;

    /**
     * 특정 도서 ID를 기준으로 유사한 도서를 추천합니다.
     */
    @Transactional(readOnly = true)
    public Page<BookRecommendationDTO> getRecommendationsByBookId(Long bookId, Pageable pageable) {
        BookVector targetVector = bookVectorRepository.findById(bookId)
                .orElseThrow(() -> new RuntimeException("해당 도서의 벡터 데이터가 존재하지 않습니다."));

        String vectorString = Arrays.toString(targetVector.getVector());

        // 자기 자신(bookId)을 제외하고 검색
        return getRecommendations(vectorString, bookId, pageable);
    }

    /**
     * [공통] 사용자 취향 벡터 기반 도서 추천
     */
    @Transactional(readOnly = true)
    public Page<BookRecommendationDTO> getRecommendationsByVector(String vector, Pageable pageable) {
        // 취향 기반 검색이므로 제외할 ID 없음 (null)
        return getRecommendations(vector, null, pageable);
    }

    /**
     * 내부 공통 추천 로직 (Page 변환 처리)
     */
    private Page<BookRecommendationDTO> getRecommendations(String vectorString, Long excludeId, Pageable pageable) {
        // 1. 유사도 기반으로 도서 ID와 Score 리스트를 먼저 가져옴 (1번의 쿼리)
        Page<Object[]> results = bookVectorRepository.findSimilarBookIds(vectorString, excludeId, pageable);
//        Page<Object[]> results = chapterVectorRepository.findSimilarBookIdsByChapters(vectorString, excludeId, pageable);


        // 2. 검색된 ID들만 리스트로 추출
        List<Long> bookIds = results.getContent().stream()
                .map(result -> ((Number) result[0]).longValue())
                .toList();

        // 3. 추출된 ID들에 해당하는 도서 정보들을 한 번에 조회 (In-clause 사용, 1번의 쿼리)
        // findById 대신 findAllById를 사용하여 N+1 문제를 해결합니다.
        Map<Long, Book> bookMap = bookRepository.findAllById(bookIds).stream()
                .collect(Collectors.toMap(Book::getBookId, book -> book));

        // 4. 원래의 유사도 순서를 유지하며 DTO로 변환
        return results.map(result -> {
            Long id = ((Number) result[0]).longValue();
            Double score = ((Number) result[1]).doubleValue();

            Book book = bookMap.get(id);
            if (book == null) throw new RuntimeException("도서 정보를 찾을 수 없습니다. ID: " + id);

            // Score를 DTO에 함께 담아주면 프론트엔드에서 "유사도 98%" 같은 표시가 가능해집니다.
            return BookRecommendationDTO.from(book, score);
        });
    }

    private float[] getEmbeddingFromPython(String text) {
        return embeddingServerWebClient.post()
                .uri("/api/v1/embed-text")
                .bodyValue(Map.of("text", text))
                .retrieve()
                .bodyToMono(BookVectorDTO.class)
                .map(BookVectorDTO::getEmbedding)
                .timeout(Duration.ofMinutes(4)) // API 외부 호출 고려하여 넉넉히 설정
                .block();
    }
    /**
     * [추가] 사용자가 입력한 텍스트로 유사 도서를 추천합니다.
     */
    @Transactional(readOnly = true)
    public Page<BookRecommendationDTO> getRecommendationsByText(String text, Pageable pageable) {
        // 1. 파이썬 서버 호출 -> 허깅페이스 임베딩 획득
        float[] vector = getEmbeddingFromPython(text);

        // 2. pgvector 검색을 위해 float[]을 "[0.1, 0.2, ...]" 형태의 문자열로 변환
        String vectorString = Arrays.toString(vector);

        // 3. 기존 검색 로직(findSimilarBookIds) 호출
        return getRecommendations(vectorString, null, pageable);
    }

    /**
     * [관리자] 챕터 벡터 기반 북 벡터 생성/갱신
     */
    @Transactional
    public void createBookVectorFromChapters(Long bookId) {
        // 1. 챕터 벡터 가져오기 및 평균 계산
        List<Chapter> chapters = chapterRepository.findAllByBook_BookId(bookId);
        if (chapters.isEmpty()) {
            throw new RuntimeException("임베딩된 챕터가 없어 북 벡터를 생성할 수 없습니다.");
        }
        List<Integer> paragraphCounts = chapters.stream().map(Chapter::getParagraphs).toList();

//        List<float[]> chapterVectors = getChapterVectorsForBook(bookId);
        List<float[]> chapterVectors = chapterVectorRepository.findAllVectorsByBookId(bookId);
        if (chapterVectors.isEmpty()) {
            throw new RuntimeException("임베딩된 챕터가 없어 북 벡터를 생성할 수 없습니다.");
        }

        float[] averagedVector = calculateOptimizedBookVector(chapterVectors, paragraphCounts);

        // 2. 도서 엔티티 존재 확인 (신규 생성 시 연관 관계 설정을 위해 필요)
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new RuntimeException("도서가 존재하지 않습니다."));

        // 3. [Upsert 로직] 기존 벡터가 있으면 가져와서 수정하고, 없으면 새로 생성
        // findByBookId 또는 findById를 사용하여 기존 데이터를 조회합니다.
        BookVector bookVector = bookVectorRepository.findById(bookId)
                .map(existingVector -> {
                    // [Case 1] 기존 데이터가 있다면? -> 값만 갱신 (Dirty Checking 활용)
                    existingVector.updateVector(averagedVector);
                    return existingVector;
                })
                .orElseGet(() -> {
                    // [Case 2] 기존 데이터가 없다면? -> Builder로 새 객체 생성
                    return BookVector.builder()
                            .book(book)
                            .vector(averagedVector)
                            .build();
                });

        // 4. 저장 (JPA가 상황에 맞춰 Insert 또는 Update 쿼리를 날립니다)
        bookVectorRepository.save(bookVector);
    }

    private float[] calculateOptimizedBookVector(List<float[]> vectors, List<Integer> paragraphCounts) {
        int dim = vectors.get(0).length;
        float[] resultVector = new float[dim];
        double totalWeight = 0;
        float threshold = 0.001f;

        for (int i = 0; i < vectors.size(); i++) {
            float[] v = vectors.get(i);
            double weight = Math.log1p(paragraphCounts.get(i));
            totalWeight += weight;

            for (int j = 0; j < dim; j++) {
                if (Math.abs(v[j]) > threshold) {
                    resultVector[j] += (float) (v[j] * weight);
                }
            }
        }
        return normalize(resultVector);
    }

    private float[] normalize(float[] vector) {
        double sumSq = 0;
        for (float v : vector) sumSq += v * v;
        float norm = (float) Math.sqrt(sumSq);
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) vector[i] /= norm;
        }
        return vector;
    }

//    @Transactional(readOnly = true)
//    public List<float[]> getChapterVectorsForBook(Long bookId) {
//        // 1. DB에서 문자열 형태로 가져오기
//        List<String> vectorStrings = chapterVectorRepository.findAllVectorsByBookId(bookId);
//
//        if (vectorStrings == null || vectorStrings.isEmpty()) {
//            return Collections.emptyList();
//        }
//
//        // 2. 문자열을 float 배열로 수동 파싱
//        return vectorStrings.stream()
//                .map(this::parseVectorString)
//                .collect(Collectors.toList());
//    }
//
//    private float[] parseVectorString(String vectorStr) {
//        // PostgreSQL vector 포맷인 "[0.1,0.2,...]"에서 대괄호 제거 후 쉼표로 분리
//        String cleanStr = vectorStr.replace("[", "").replace("]", "");
//        String[] parts = cleanStr.split(",");
//
//        float[] vector = new float[parts.length];
//        for (int i = 0; i < parts.length; i++) {
//            vector[i] = Float.parseFloat(parts[i].trim());
//        }
//        return vector;
//    }

    @Transactional
    @Async
    public void processFullBookEmbedding(Long bookId) {
        // 1. 데이터 준비
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new RuntimeException("도서가 존재하지 않습니다."));
        List<Chapter> chapters = chapterRepository.findAllByBook_BookId(bookId);
        if (chapters.isEmpty()) throw new RuntimeException("처리할 챕터가 없습니다.");

        // 2. 파이썬 배치 호출 (경로 리스트 전달)
        List<String> paths = chapters.stream().map(Chapter::getBookContentPath).toList();
        BatchVectorResponseDTO response = embeddingServerWebClient.post()
                .uri("/api/v1/embed-batch")
                .bodyValue(Map.of("paths", paths))
                .retrieve()
                .bodyToMono(BatchVectorResponseDTO.class)
                .timeout(Duration.ofMinutes(5))
                .block();

        if (response == null || response.getChapterVectors().isEmpty()) {
            throw new RuntimeException("임베딩 서버로부터 벡터를 받지 못했습니다.");
        }

        // 3. 챕터 벡터 저장 (Upsert)
        List<float[]> chapterVectors = response.getChapterVectors();
        for (int i = 0; i < chapters.size(); i++) {
            chapterVectorService.saveOrUpdateChapterVector(chapters.get(i), chapterVectors.get(i));
        }

        // 4. [사용자님 로직 핵심] 최적화된 북 벡터 계산
        // 파이썬이 준 단순 평균 대신, 사용자님의 가중치 산식을 사용합니다.
        List<Integer> paragraphCounts = chapters.stream().map(Chapter::getParagraphs).toList();
        float[] optimizedAveragedVector = calculateOptimizedBookVector(chapterVectors, paragraphCounts);

        // 5. 북 벡터 저장 (Upsert)
        saveOrUpdateBookVector(book, optimizedAveragedVector);

    }

    private void saveOrUpdateBookVector(Book book, float[] vector) {
        BookVector bookVector = bookVectorRepository.findById(book.getBookId())
                .map(existing -> {
                    existing.updateVector(vector);
                    return existing;
                })
                .orElseGet(() -> BookVector.builder().book(book).vector(vector).build());
        bookVectorRepository.save(bookVector);
    }
}
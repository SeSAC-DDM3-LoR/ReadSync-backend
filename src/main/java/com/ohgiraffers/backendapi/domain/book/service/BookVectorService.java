package com.ohgiraffers.backendapi.domain.book.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * 특정 도서 ID를 기준으로 유사한 도서를 추천합니다.
     */
    @Transactional(readOnly = true)
    public Page<BookVectorDTO> getRecommendationsByBookId(Long bookId, Pageable pageable) {
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
    public Page<BookVectorDTO> getRecommendationsByVector(String vector, Pageable pageable) {
        // 취향 기반 검색이므로 제외할 ID 없음 (null)
        return getRecommendations(vector, null, pageable);
    }

    /**
     * 내부 공통 추천 로직 (Page 변환 처리)
     */
    private Page<BookVectorDTO> getRecommendations(String vectorString, Long excludeId, Pageable pageable) {
        // 1. 유사도 기반으로 도서 ID와 Score 리스트를 먼저 가져옴 (1번의 쿼리)
        Page<Object[]> results = bookVectorRepository.findSimilarBookIds(vectorString, excludeId, pageable);

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
            return BookVectorDTO.from(book);
        });
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
        float threshold = 0.01f; // 이 값보다 작은 차원값은 노이즈로 간주

        for (int i = 0; i < vectors.size(); i++) {
            float[] v = vectors.get(i);
            // 가중치: 텍스트 길이 (로그를 씌워 너무 극단적인 차이를 방지하거나, 그냥 길이를 사용)
            double weight = Math.log1p(paragraphCounts.get(i));
            totalWeight += weight;

            for (int j = 0; j < dim; j++) {
                // 핵심: 절대값이 임계치를 넘는 '의미 있는 값'만 가중치 적용하여 합산
                if (Math.abs(v[j]) > threshold) {
                    resultVector[j] += (float) (v[j] * weight);
                }
            }
        }

        // 최종 정규화 (Vector Normalization)
        return normalize(resultVector);
    }

    private float[] normalize(float[] vector) {
        double sumSq = 0;
        for (float v : vector) sumSq += v * v;

        float norm = (float) Math.sqrt(sumSq);
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
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
}
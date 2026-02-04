package com.ohgiraffers.backendapi.domain.book.service;

import com.ohgiraffers.backendapi.domain.book.dto.BatchVectorResponseDTO;
import com.ohgiraffers.backendapi.domain.book.dto.BookRecommendationDTO;
import com.ohgiraffers.backendapi.domain.book.dto.BookVectorDTO;
import com.ohgiraffers.backendapi.domain.book.entity.Book;
import com.ohgiraffers.backendapi.domain.book.entity.BookVector;
import com.ohgiraffers.backendapi.domain.book.repository.BookRepository;
import com.ohgiraffers.backendapi.domain.book.repository.BookVectorRepository;
import com.ohgiraffers.backendapi.domain.chapter.entity.Chapter;
import com.ohgiraffers.backendapi.domain.chapter.repository.ChapterRepository;
import com.ohgiraffers.backendapi.domain.chapter.repository.ChapterVectorRepository;
import com.ohgiraffers.backendapi.domain.chapter.service.ChapterVectorService;
import com.ohgiraffers.backendapi.domain.user.entity.UserPreference;
import com.ohgiraffers.backendapi.domain.user.repository.UserPreferenceRepository;
import com.ohgiraffers.backendapi.domain.library.repository.LibraryRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
    private final UserPreferenceRepository userPreferenceRepository;
    private final LibraryRepository libraryRepository;
    private final PlatformTransactionManager transactionManager; // 트랜잭션 수동 제어를 위한 매니저

    /**
     * 특정 도서 ID를 기준으로 유사한 도서를 추천합니다.
     */
    @Transactional(readOnly = true)
    public Page<BookRecommendationDTO> getRecommendationsByBookId(Long bookId, Pageable pageable) {
        BookVector targetVector = bookVectorRepository.findById(bookId)
                .orElseThrow(() -> new RuntimeException("해당 도서의 벡터 데이터가 존재하지 않습니다."));

        String vectorString = Arrays.toString(targetVector.getVector());

        // 자기 자신(bookId)을 제외하고 검색
        return getRecommendations(vectorString, Collections.singletonList(bookId), pageable); // List로 변경
    }

    /**
     * [공통] 사용자 장기 취향 벡터 기반 도서 추천
     */
    @Transactional(readOnly = true)
    public Page<BookRecommendationDTO> getRecommendationsByVector(Long userId, Pageable pageable) {
        UserPreference userPreference = userPreferenceRepository.findById(userId).orElse(null);
        if (userPreference == null || userPreference.getVector() == null) {
            // [Fix] 취향 데이터가 없으면 빈 결과를 반환하여 프론트엔드에서 랜덤 추천(Fallback)을 수행하도록 유도
            return Page.empty(pageable);
        }
        String vectorString = Arrays.toString(userPreference.getVector());

        // [수정] 사용자가 이미 소유한(라이브러리에 있는) 모든 도서 ID 가져오기
        List<Long> excludeIds = libraryRepository.findBookIdsByUserId(userId);

        return getRecommendations(vectorString, excludeIds, pageable);
    }

    /**
     * 내부 공통 추천 로직 (Page 변환 처리)
     */
    private Page<BookRecommendationDTO> getRecommendations(String vectorString, List<Long> excludeIds,
            Pageable pageable) {
        // [수정] excludeIds가 비어있으면 null 처리하여 쿼리 오류 방지
        boolean hasExcludes = excludeIds != null && !excludeIds.isEmpty();
        if (!hasExcludes) {
            excludeIds = Collections.singletonList(-1L); // 빈 리스트일 경우 더미값
        }

        // 1. 유사도 기반으로 도서 ID와 Score 리스트를 먼저 가져옴 (1번의 쿼리)
        Page<Object[]> results = bookVectorRepository.findSimilarBookIds(vectorString, excludeIds, hasExcludes,
                pageable);

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
            if (book == null)
                throw new RuntimeException("도서 정보를 찾을 수 없습니다. ID: " + id);

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

        // List<float[]> chapterVectors = getChapterVectorsForBook(bookId);
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
        int n = vectors.size();
        float[] resultVector = new float[dim];

        // 1. 챕터별 기본 가중치 (문단 수 기반 - SQRT 사용으로 영향력 강화)
        double[] lengthWeights = new double[n];
        for (int i = 0; i < n; i++) {
            // Log 대신 Sqrt를 사용하여 긴 챕터의 중요도를 더 높임
            lengthWeights[i] = Math.sqrt(paragraphCounts.get(i));
        }

        // 2. 유사도 중심성(Centrality) 계산 (챕터가 2개 이상일 때만 의미 있음)
        double[] centralityWeights = new double[n];
        if (n > 1) {
            for (int i = 0; i < n; i++) {
                double similaritySum = 0;
                for (int j = 0; j < n; j++) {
                    if (i == j)
                        continue;
                    // 코사인 유사도 계산 (0~1 범위로 가정, 음수일 경우 0 처리)
                    double sim = calculateCosineSimilarity(vectors.get(i), vectors.get(j));
                    similaritySum += Math.max(0, sim);
                }
                // 평균 유사도를 중심성 점수로 사용
                centralityWeights[i] = similaritySum / (n - 1);
            }
        } else {
            centralityWeights[0] = 1.0;
        }

        // 3. 최종 가중치 적용 및 벡터 합산
        double totalWeightCheck = 0;

        for (int i = 0; i < n; i++) {
            // 최종 가중치 = (문단 수 가중치) * (1 + 중심성 가중치)
            // 중심성이 0일 수도 있으므로 1을 더해 기본값을 보장하거나, 곱하기 방식으로 조절
            double finalWeight = lengthWeights[i] * (0.5 + centralityWeights[i]);
            // 0.5를 더하는 이유: 중심성이 낮아도 문단 수가 많으면 어느 정도 반영하기 위함

            float[] v = vectors.get(i);
            for (int j = 0; j < dim; j++) {
                resultVector[j] += (float) (v[j] * finalWeight);
            }
            totalWeightCheck += finalWeight;
        }

        // 4. 정규화 (방향만 중요하므로 L2 Norm 적용)
        return normalize(resultVector);
    }

    // 코사인 유사도 계산 헬퍼
    private double calculateCosineSimilarity(float[] v1, float[] v2) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < v1.length; i++) {
            dotProduct += v1[i] * v2[i];
            normA += v1[i] * v1[i];
            normB += v2[i] * v2[i];
        }
        if (normA == 0 || normB == 0)
            return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private float[] normalize(float[] vector) {
        double sumSq = 0;
        for (float v : vector)
            sumSq += v * v;
        float norm = (float) Math.sqrt(sumSq);
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++)
                vector[i] /= norm;
        }
        return vector;
    }

    // @Transactional(readOnly = true)
    // public List<float[]> getChapterVectorsForBook(Long bookId) {
    // // 1. DB에서 문자열 형태로 가져오기
    // List<String> vectorStrings =
    // chapterVectorRepository.findAllVectorsByBookId(bookId);
    //
    // if (vectorStrings == null || vectorStrings.isEmpty()) {
    // return Collections.emptyList();
    // }
    //
    // // 2. 문자열을 float 배열로 수동 파싱
    // return vectorStrings.stream()
    // .map(this::parseVectorString)
    // .collect(Collectors.toList());
    // }
    //
    // private float[] parseVectorString(String vectorStr) {
    // // PostgreSQL vector 포맷인 "[0.1,0.2,...]"에서 대괄호 제거 후 쉼표로 분리
    // String cleanStr = vectorStr.replace("[", "").replace("]", "");
    // String[] parts = cleanStr.split(",");
    //
    // float[] vector = new float[parts.length];
    // for (int i = 0; i < parts.length; i++) {
    // vector[i] = Float.parseFloat(parts[i].trim());
    // }
    // return vector;
    // }

    @Transactional
    @Async
    public void processFullBookEmbedding(Long bookId) {
        System.out.println(
                "🚀 [Async Start] 도서 ID " + bookId + " 처리 시작 (Thread: " + Thread.currentThread().getName() + ")");

        // 1. 데이터 준비
        // [수정] Book 엔티티는 콜백 내부에서 필요할 때 조회하므로 여기서는 ID만으로 충분합니다.
        List<Chapter> chapters = chapterRepository.findAllByBook_BookId(bookId);
        if (chapters.isEmpty())
            throw new RuntimeException("처리할 챕터가 없습니다.");

        // 2. 파이썬 배치 호출을 위한 ID 및 경로 리스트 추출
        // [중요] 비동기 콜백(subscribe) 내에서는 위에서 조회한 book, chapters 엔티티를 직접 사용하면 안 됨.
        // 트랜잭션이 종료된 후 사용하게 되어 "detached entity passed to persist" 에러 발생함.
        // 따라서 ID만 추출해두고, 콜백 내부에서 다시 조회해야 함.
        List<String> paths = chapters.stream().map(Chapter::getBookContentPath).toList();
        List<Long> chapterIds = chapters.stream().map(Chapter::getChapterId).toList();

        // [수정] block() 제거하고 subscribe()로 완전 비동기 처리
        // 이렇게 하면 스레드가 대기하지 않고 즉시 반환되며, 파이썬 응답이 오면 콜백이 실행됩니다.
        embeddingServerWebClient.post()
                .uri("/api/v1/embed-batch")
                .bodyValue(Map.of("paths", paths))
                .retrieve()
                .bodyToMono(BatchVectorResponseDTO.class)
                .timeout(Duration.ofMinutes(30))
                .subscribe(response -> {
                    // 성공 시 콜백
                    if (response == null || response.getChapterVectors().isEmpty()) {
                        System.err.println("⚠️ 임베딩 서버 응답이 비어있습니다. Book ID: " + bookId);
                        return;
                    }

                    try {
                        // [중요] 비동기 콜백은 별도 스레드에서 실행되므로 트랜잭션 범위를 명시적으로 설정해야 함
                        // 그렇지 않으면 조회한 엔티티가 즉시 Detached 상태가 되어, 저장 시 에러 발생
                        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                            Book managedBook = bookRepository.findById(bookId).orElseThrow();

                            // 3. 챕터 벡터 저장 (Upsert)
                            List<float[]> chapterVectors = response.getChapterVectors();

                            for (int i = 0; i < chapterIds.size(); i++) {
                                if (i >= chapterVectors.size())
                                    break;
                                Long cId = chapterIds.get(i);
                                final int index = i;
                                chapterVectorService.saveVectorForChapter(cId, chapterVectors.get(index));
                            }

                            // 4. 북 벡터 저장
                            float[] optimizedAveragedVector;
                            if (response.getBookVector() != null && response.getBookVector().length > 0) {
                                optimizedAveragedVector = response.getBookVector();
                            } else {
                                throw new RuntimeException("파이썬 서버로부터 북 벡터를 받지 못했습니다.");
                            }

                            saveOrUpdateBookVector(managedBook, optimizedAveragedVector);
                            System.out.println("✅ 도서 벡터 갱신 완료: " + managedBook.getTitle());
                        });

                    } catch (Exception e) {
                        System.err.println("❌ 벡터 저장 중 오류 발생: " + e.getMessage());
                        // e.printStackTrace();
                    }

                }, error -> {
                    // 실패 시 콜백
                    System.err.println("❌ 파이썬 서버 통신 오류 (Book ID " + bookId + "): " + error.getMessage());
                });
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
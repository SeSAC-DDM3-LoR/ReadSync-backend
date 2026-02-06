package com.ohgiraffers.backendapi.domain.feature;

import com.ohgiraffers.backendapi.domain.book.repository.BookVectorRepository;
import com.ohgiraffers.backendapi.domain.category.entity.Category;
import com.ohgiraffers.backendapi.domain.category.repository.CategoryRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenreVectorCache {

    private final CategoryRepository categoryRepository;
    private final BookVectorRepository bookVectorRepository;

    private final Map<String, float[]> genreAverageVectors = new HashMap<>();

    @PostConstruct
    public void init() {
        refreshCache();
    }

    // 매일 새벽 4시에 장르별 평균 벡터 갱신 (신규 도서 반영)
    @Scheduled(cron = "0 0 4 * * *")
    public void refreshCache() {
        log.info(" [GenreVectorCache] 장르별 평균 벡터 계산 시작...");
        List<Category> categories = categoryRepository.findAll();

        int totalGenres = 0;
        for (Category category : categories) {
            String genreName = category.getCategoryName();
            if (genreName == null || genreName.equals("General"))
                continue; // General은 스킵하거나 별도 처리

            try {
                // 해당 카테고리의 모든 책 벡터 조회
                List<Float[]> vectorList = bookVectorRepository.findAllVectorsByCategory(category.getCategoryId());

                if (vectorList == null || vectorList.isEmpty()) {
                    log.debug("   - [Skip] '{}' 장르: 등록된 책/벡터 없음", genreName);
                    continue;
                }

                float[] averageVector = calculateAverage(vectorList);
                genreAverageVectors.put(genreName, averageVector);
                totalGenres++;
                log.debug("   - [Cache] '{}' 장르 평균 벡터 캐싱 완료 (샘플 수: {})", genreName, vectorList.size());

            } catch (Exception e) {
                log.warn("   - [Error] '{}' 장르 벡터 계산 중 오류: {}", genreName, e.getMessage());
            }
        }
        log.info("✅ [GenreVectorCache] 총 {}개 장르의 평균 벡터 캐싱 완료.", totalGenres);
    }

    private float[] calculateAverage(List<Float[]> vectors) {
        int dim = 1024; // 벡터 차원
        float[] sumVector = new float[dim];
        int count = vectors.size();

        for (Float[] vecObj : vectors) {
            if (vecObj == null || vecObj.length != dim)
                continue;
            for (int i = 0; i < dim; i++) {
                sumVector[i] += vecObj[i]; // Float -> float unboxing
            }
        }

        // 평균 -> 정규화
        float[] avgVector = new float[dim];
        double sumSq = 0.0;
        for (int i = 0; i < dim; i++) {
            avgVector[i] = sumVector[i] / count;
            sumSq += avgVector[i] * avgVector[i];
        }

        // L2 Normalize
        float norm = (float) Math.sqrt(sumSq);
        if (norm > 1e-9) {
            for (int i = 0; i < dim; i++) {
                avgVector[i] /= norm;
            }
        }
        return avgVector;
    }

    public float[] getGenreVector(String genreName) {
        return genreAverageVectors.get(genreName);
    }
}

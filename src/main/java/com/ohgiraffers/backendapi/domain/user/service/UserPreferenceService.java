package com.ohgiraffers.backendapi.domain.user.service;

import com.ohgiraffers.backendapi.domain.book.entity.Book;
import com.ohgiraffers.backendapi.domain.chapter.entity.Chapter;
import com.ohgiraffers.backendapi.domain.chapter.entity.ChapterVector;
import com.ohgiraffers.backendapi.domain.chapter.repository.ChapterRepository;
import com.ohgiraffers.backendapi.domain.chapter.repository.ChapterVectorRepository;
import com.ohgiraffers.backendapi.domain.feature.GenreVectorCache;
import com.ohgiraffers.backendapi.domain.user.entity.User;
import com.ohgiraffers.backendapi.domain.user.entity.UserPreference;
import com.ohgiraffers.backendapi.domain.user.repository.UserPreferenceRepository;
import com.ohgiraffers.backendapi.domain.user.repository.UserRepository;
import com.ohgiraffers.backendapi.global.common.annotation.CurrentUserId;
import com.ohgiraffers.backendapi.global.error.CustomException;
import com.ohgiraffers.backendapi.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    private final UserPreferenceRepository preferenceRepository;
    private final ChapterVectorRepository chapterVectorRepository;
    private final ChapterRepository chapterRepository;
    private final UserRepository userRepository;
    private final com.ohgiraffers.backendapi.domain.library.repository.LibraryRepository libraryRepository; // [New]
    private final GenreVectorCache genreVectorCache; // [New]

    // 기본 학습률 설정
    // 기본 학습률 설정 (2026-02-06 Updated: 초기화 로직 도입으로 반응성 향상)
    private static final float ALPHA_LONG = 0.1f; // 장기 취향 (0.05 -> 0.1)
    private static final float ALPHA_SHORT = 0.4f; // 단기 취향 (0.3 -> 0.4)

    // [New] 초기 부스팅 설정 (First 5 Books -> 50:50)
    private static final int READ_COUNT_THRESHOLD = 5;
    private static final float ALPHA_BOOST = 0.5f;

    // @Transactional // ReadingEventListener에서 트랜잭션 관리
    public void updatePreferenceByIncrement(Long userId, Long chapterId, int newlyReadCount, int totalParagraphs) {
        // 1. 취향 벡터 로드
        UserPreference pref = preferenceRepository.findByUser_Id(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

                    // [New] 신규 유저 생성 시 장르 기반 초기화 수행
                    UserPreference newPref = new UserPreference(user);
                    initializeVectorFromGenre(user, newPref);
                    return preferenceRepository.save(newPref);
                });

        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAPTER_NOT_FOUND));
        // 2. 해당 챕터의 임베딩 벡터 로드
        ChapterVector chapterVector = chapterVectorRepository.findByChapter(chapter)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAPTER_NOT_FOUND));

        float[] chapterVec = chapterVector.getVector();
        // 3. 새로 읽은 문단 수에 따른 가중치(Alpha) 계산
        // 한 문단당 가중치 = 전체 학습률 / 총 문단 수
        // 3. 학습률 결정 (초기 유저 부스팅 로직 적용)
        // [Update 2026-02-06] "읽다 만 책"은 제외하고, 진행률 30% 이상인 '유의미한 독서'만 카운트
        long userBookCount = libraryRepository.countByUserIdAndDeletedAtIsNullAndTotalProgressGreaterThanEqual(userId,
                new java.math.BigDecimal("30.00"));
        float currentAlphaLong, currentAlphaShort;

        if (userBookCount <= READ_COUNT_THRESHOLD) {
            // 초기 5권 이내: 50%의 강한 가중치 적용 (빠른 적응)
            currentAlphaLong = ALPHA_BOOST;
            currentAlphaShort = ALPHA_BOOST;
            log.info("🔥 [UserBoosting] 초기 유저(BookCount: {}) - 부스팅 학습률 적용 (0.5)", userBookCount);
        } else {
            // 그 이후: 일반 학습률 적용
            currentAlphaLong = ALPHA_LONG;
            currentAlphaShort = ALPHA_SHORT;
        }

        // 3-1. 새로 읽은 문단 수 비례 조정 (이미 부스팅 상태면 부스팅 비율 유지 or 문단 수 비례할지 결정 -> 여기선 문단 수 비례
        // 적용하되 기본 Alpha가 큼)
        // 단, 부스팅 모드일 때 문단 수 비율을 어떻게 할지? -> 부스팅은 "책 단위" 영향력이므로 문단 수 비례보다는 고정 0.5가 더 적절할
        // 수 있음.
        // 하지만 "읽다 만 책"에 50%를 주는 건 위험하므로, "완독율"을 곱하는 게 안전함.

        float progressRate = (float) newlyReadCount / totalParagraphs;
        float finalWeightLong = currentAlphaLong * progressRate;
        float finalWeightShort = currentAlphaShort * progressRate;

        // 4. 지수 이동 평균(EMA) 적용
        float[] updatedLong = applyEma(pref.getVector(), chapterVec, finalWeightLong);
        float[] updatedShort = applyEma(pref.getShortTermVector(), chapterVec, finalWeightShort);

        // 5. 엔티티 반영
        pref.updateTaste(updatedLong, updatedShort);
        log.info("📊 [VectorUpdate] 유저({}) - 챕터({}) 반영 완료 | 가중치(Long={}/Short={}) | 부스팅: {}",
                pref.getUser().getUserInformation().getNickname(), chapterId, currentAlphaLong, currentAlphaShort,
                (currentAlphaLong == ALPHA_BOOST));
    }

    private float[] applyEma(float[] oldVec, float[] chapterVec, float weight) {
        if (oldVec == null) {
            oldVec = new float[1024];
        }

        float[] newVec = new float[1024];
        float sumSq = 0;

        for (int i = 0; i < 1024; i++) {
            // 새 벡터 = 기존(비중 1-w) + 신규(비중 w)
            newVec[i] = (1 - weight) * oldVec[i] + weight * chapterVec[i];
            sumSq += newVec[i] * newVec[i];
        }

        // 코사인 유사도를 위해 L2 정규화 (길이를 1로 맞춤)
        float norm = (float) Math.sqrt(sumSq);
        if (norm > 1e-9) {
            for (int i = 0; i < 1024; i++)
                newVec[i] /= norm;
        }
        return newVec;
    }

    // [신규] 진행률(30, 70, 100%) 도달 시 호출: 가중치 차등 적용 (Cumulative Weighting)
    @Transactional
    public void updatePreferenceByProgress(Long userId, Long chapterId, float totalMultiplier) {
        // 1. 취향 벡터 로드
        UserPreference pref = preferenceRepository.findByUser_Id(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
                    return preferenceRepository.save(new UserPreference(user));
                });

        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAPTER_NOT_FOUND));
        // 2. 해당 챕터의 임베딩 벡터 로드 (없으면 스킵)
        ChapterVector chapterVector = chapterVectorRepository.findByChapter(chapter)
                .orElse(null);

        if (chapterVector == null) {
            // 벡터가 없으면 취향 분석을 할 수 없으므로 조용히 리턴 (트랜잭션 롤백 방지)
            return;
        }

        float[] chapterVec = chapterVector.getVector();

        // 3. 학습률 결정 (초기 유저 부스팅 로직 적용)
        // [Update 2026-02-06] "읽다 만 책"은 제외하고, 진행률 30% 이상인 '유의미한 독서'만 카운트
        long userBookCount = libraryRepository.countByUserIdAndDeletedAtIsNullAndTotalProgressGreaterThanEqual(userId,
                new java.math.BigDecimal("30.00"));
        float currentAlphaLong, currentAlphaShort;

        if (userBookCount <= READ_COUNT_THRESHOLD) {
            currentAlphaLong = ALPHA_BOOST;
            currentAlphaShort = ALPHA_BOOST;
            log.info("🔥 [UserBoosting-Progress] 초기 유저(BookCount: {}) - 부스팅 적용 (0.5)", userBookCount);
        } else {
            currentAlphaLong = ALPHA_LONG;
            currentAlphaShort = ALPHA_SHORT;
        }

        // 3-1. [Update] 외부에서 계산된 누적 가중치(Multiplier)를 그대로 적용
        float weightLong = currentAlphaLong * totalMultiplier;
        float weightShort = currentAlphaShort * totalMultiplier;

        // 4. 지수 이동 평균(EMA) 적용
        float[] updatedLong = applyEma(pref.getVector(), chapterVec, weightLong);
        float[] updatedShort = applyEma(pref.getShortTermVector(), chapterVec, weightShort);

        // 5. 엔티티 반영
        pref.updateTaste(updatedLong, updatedShort);
        log.info("📊 [VectorUpdate-Progress] 유저({}) - 챕터({}) 반영 완료 | 가중치(Long={}/Short={}) | 부스팅: {}",
                pref.getUser().getUserInformation().getNickname(), chapterId, currentAlphaLong, currentAlphaShort,
                (currentAlphaLong == ALPHA_BOOST));
    }

    // [New] 장르 기반 벡터 초기화 로직 (다중 장르 지원)
    private void initializeVectorFromGenre(User user, UserPreference pref) {
        try {
            if (user.getUserInformation() != null && user.getUserInformation().getPreferredGenre() != null) {
                String genreStr = user.getUserInformation().getPreferredGenre();
                String[] genres = genreStr.split(",");

                float[] combinedVec = new float[1024];
                int validCount = 0;

                for (String g : genres) {
                    String cleanGenre = g.trim();
                    float[] vec = genreVectorCache.getGenreVector(cleanGenre);
                    if (vec != null) {
                        for (int i = 0; i < 1024; i++) {
                            combinedVec[i] += vec[i];
                        }
                        validCount++;
                    }
                }

                if (validCount > 0) {
                    // 평균 계산 및 정규화
                    float sumSq = 0;
                    for (int i = 0; i < 1024; i++) {
                        combinedVec[i] /= validCount;
                        sumSq += combinedVec[i] * combinedVec[i];
                    }

                    // L2 Normalize
                    float norm = (float) Math.sqrt(sumSq);
                    if (norm > 1e-9) {
                        for (int i = 0; i < 1024; i++)
                            combinedVec[i] /= norm;
                    }

                    log.info("✨ [UserInit] 유저({})의 초기 취향을 '{}' 장르들의 평균으로 설정합니다.",
                            user.getUserInformation().getNickname(), genreStr);
                    pref.updateTaste(combinedVec, combinedVec);
                } else {
                    log.debug("   [UserInit] '{}' 장르 벡터가 캐시에 없어 기본 0 벡터로 시작합니다.", genreStr);
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ [UserInit] 초기화 중 오류 발생 (기본 0 벡터 사용): {}", e.getMessage());
        }
    }
}

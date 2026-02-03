package com.ohgiraffers.backendapi.domain.user.service;

import com.ohgiraffers.backendapi.domain.book.entity.Book;
import com.ohgiraffers.backendapi.domain.chapter.entity.Chapter;
import com.ohgiraffers.backendapi.domain.chapter.entity.ChapterVector;
import com.ohgiraffers.backendapi.domain.chapter.repository.ChapterRepository;
import com.ohgiraffers.backendapi.domain.chapter.repository.ChapterVectorRepository;
import com.ohgiraffers.backendapi.domain.user.entity.User;
import com.ohgiraffers.backendapi.domain.user.entity.UserPreference;
import com.ohgiraffers.backendapi.domain.user.repository.UserPreferenceRepository;
import com.ohgiraffers.backendapi.domain.user.repository.UserRepository;
import com.ohgiraffers.backendapi.global.common.annotation.CurrentUserId;
import com.ohgiraffers.backendapi.global.error.CustomException;
import com.ohgiraffers.backendapi.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    private final UserPreferenceRepository preferenceRepository;
    private final ChapterVectorRepository chapterVectorRepository;
    private final ChapterRepository chapterRepository;
    private final UserRepository userRepository;

    // 기본 학습률 설정
    private static final float ALPHA_LONG = 0.05f; // 장기 취향 (천천히 변화)
    private static final float ALPHA_SHORT = 0.3f; // 단기 취향 (빠르게 변화)

    // @Transactional // ReadingEventListener에서 트랜잭션 관리
    public void updatePreferenceByIncrement(Long userId, Long chapterId, int newlyReadCount, int totalParagraphs) {
        // 1. 취향 벡터 로드
        UserPreference pref = preferenceRepository.findByUser_Id(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
                    return preferenceRepository.save(new UserPreference(user));
                });

        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAPTER_NOT_FOUND));
        // 2. 해당 챕터의 임베딩 벡터 로드
        ChapterVector chapterVector = chapterVectorRepository.findByChapter(chapter)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAPTER_NOT_FOUND));

        float[] chapterVec = chapterVector.getVector();
        // 3. 새로 읽은 문단 수에 따른 가중치(Alpha) 계산
        // 한 문단당 가중치 = 전체 학습률 / 총 문단 수
        float weightLong = (ALPHA_LONG / totalParagraphs) * newlyReadCount;
        float weightShort = (ALPHA_SHORT / totalParagraphs) * newlyReadCount;

        // 4. 지수 이동 평균(EMA) 적용
        float[] updatedLong = applyEma(pref.getVector(), chapterVec, weightLong);
        float[] updatedShort = applyEma(pref.getShortTermVector(), chapterVec, weightShort);

        // 5. 엔티티 반영
        pref.updateTaste(updatedLong, updatedShort);
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

    // [신규] 진행률(30, 70, 100%) 도달 시 호출: 가중치 차등 적용
    @Transactional
    public void updatePreferenceByProgress(Long userId, Long chapterId, int milestone) {
        // 1. 취향 벡터 로드
        UserPreference pref = preferenceRepository.findByUser_Id(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
                    return preferenceRepository.save(new UserPreference(user));
                });

        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAPTER_NOT_FOUND));
        // 2. 해당 챕터의 임베딩 벡터 로드
        ChapterVector chapterVector = chapterVectorRepository.findByChapter(chapter)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAPTER_NOT_FOUND));

        float[] chapterVec = chapterVector.getVector();

        // 3. 마일스톤 별 가중치 조절
        float multiplier = 1.0f;
        if (milestone == 30)
            multiplier = 0.5f; // 30%: 약한 반영
        else if (milestone == 70)
            multiplier = 1.0f; // 70%: 표준 반영
        else if (milestone == 100)
            multiplier = 1.5f;// 100%: 강한 반영

        float weightLong = ALPHA_LONG * multiplier;
        float weightShort = ALPHA_SHORT * multiplier;

        // 4. 지수 이동 평균(EMA) 적용
        float[] updatedLong = applyEma(pref.getVector(), chapterVec, weightLong);
        float[] updatedShort = applyEma(pref.getShortTermVector(), chapterVec, weightShort);

        // 5. 엔티티 반영
        pref.updateTaste(updatedLong, updatedShort);
    }
}

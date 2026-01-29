package com.ohgiraffers.backendapi.domain.level.service;

import com.ohgiraffers.backendapi.domain.level.dto.LevelResponse;
import com.ohgiraffers.backendapi.domain.level.entity.Level;
import com.ohgiraffers.backendapi.domain.level.repository.LevelRepository;
import com.ohgiraffers.backendapi.domain.user.entity.UserInformation;
import com.ohgiraffers.backendapi.domain.user.repository.UserInformationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class LevelService {

        private final LevelRepository levelRepository;
        private final UserInformationRepository userInformationRepository;

        /**
         * 모든 레벨 정보 조회
         */
        public List<LevelResponse> getAllLevels() {
                return levelRepository.findAllByOrderByIdAsc().stream()
                                .map(LevelResponse::from)
                                .collect(Collectors.toList());
        }

        /**
         * 특정 레벨 정보 조회
         */
        public LevelResponse getLevelById(Long levelId) {
                Level level = levelRepository.findById(levelId)
                                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 레벨입니다: " + levelId));
                return LevelResponse.from(level);
        }

        /**
         * 경험치에 해당하는 레벨 조회
         */
        public LevelResponse getLevelByExperience(int experience) {
                Level level = levelRepository.findMaxLevelByExperience(experience)
                                .orElseThrow(() -> new IllegalArgumentException("레벨을 찾을 수 없습니다."));
                return LevelResponse.from(level);
        }

        /**
         * 유저에게 경험치를 추가하고 레벨업 체크 (리셋 방식)
         * - 경험치가 다음 레벨 필요량에 도달하면 레벨업
         * - 레벨업 시 경험치는 0으로 리셋되고 초과분은 이월
         * 
         * @param userId   유저 ID
         * @param expToAdd 추가할 경험치
         * @return 레벨업 결과
         */
        @Transactional
        public LevelResponse.LevelUpResult addExperienceAndCheckLevelUp(Long userId, int expToAdd) {
                UserInformation userInfo = userInformationRepository.findByUserId(userId)
                                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다: " + userId));

                Long previousLevelId = userInfo.getLevelId();
                int currentExp = userInfo.getExperience() + expToAdd;
                Long currentLevelId = userInfo.getLevelId();

                // 모든 레벨 정보 조회
                List<Level> allLevels = levelRepository.findAllByOrderByIdAsc();

                // 레벨업 반복 체크 (여러 레벨을 한 번에 올릴 수 있음)
                while (true) {
                        // 다음 레벨 찾기
                        Level nextLevel = findNextLevel(allLevels, currentLevelId);
                        if (nextLevel == null) {
                                // 최고 레벨에 도달함
                                break;
                        }

                        // 현재 레벨에서 다음 레벨까지 필요한 경험치 계산
                        Level currentLevel = findLevelById(allLevels, currentLevelId);
                        int expNeededForNextLevel = nextLevel.getRequiredExp() - currentLevel.getRequiredExp();

                        if (currentExp >= expNeededForNextLevel) {
                                // 레벨업!
                                currentExp -= expNeededForNextLevel;
                                currentLevelId = nextLevel.getId();
                                log.info("유저 {} 레벨업! {} -> {} (남은 경험치: {})",
                                                userId, currentLevelId - 1, currentLevelId, currentExp);
                        } else {
                                // 레벨업 불가
                                break;
                        }
                }

                // 최종 상태 적용
                if (!currentLevelId.equals(previousLevelId)) {
                        userInfo.levelUpWithExpReset(currentLevelId, currentExp);
                } else {
                        userInfo.setExperience(currentExp);
                }

                // 다음 레벨까지 필요한 경험치 계산
                Integer expToNextLevel = calculateExpToNextLevel(allLevels, currentLevelId, currentExp);

                if (!currentLevelId.equals(previousLevelId)) {
                        return LevelResponse.LevelUpResult.levelUp(
                                        previousLevelId,
                                        currentLevelId,
                                        currentExp,
                                        expToNextLevel);
                }

                return LevelResponse.LevelUpResult.noChange(
                                currentLevelId,
                                currentExp,
                                expToNextLevel);
        }

        /**
         * 유저의 현재 레벨 정보 조회
         */
        public LevelResponse getUserCurrentLevel(Long userId) {
                UserInformation userInfo = userInformationRepository.findByUserId(userId)
                                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다: " + userId));

                return getLevelById(userInfo.getLevelId());
        }

        /**
         * 유저가 다음 레벨까지 필요한 경험치 계산
         */
        public Integer getExpToNextLevel(Long userId) {
                UserInformation userInfo = userInformationRepository.findByUserId(userId)
                                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다: " + userId));

                List<Level> allLevels = levelRepository.findAllByOrderByIdAsc();
                return calculateExpToNextLevel(allLevels, userInfo.getLevelId(), userInfo.getExperience());
        }

        // ==================== Helper Methods ====================

        private Level findLevelById(List<Level> levels, Long levelId) {
                return levels.stream()
                                .filter(l -> l.getId().equals(levelId))
                                .findFirst()
                                .orElse(null);
        }

        private Level findNextLevel(List<Level> levels, Long currentLevelId) {
                return levels.stream()
                                .filter(l -> l.getId().equals(currentLevelId + 1))
                                .findFirst()
                                .orElse(null);
        }

        private Integer calculateExpToNextLevel(List<Level> levels, Long currentLevelId, int currentExp) {
                Level currentLevel = findLevelById(levels, currentLevelId);
                Level nextLevel = findNextLevel(levels, currentLevelId);

                if (nextLevel == null) {
                        return null; // 최고 레벨
                }

                int expNeeded = nextLevel.getRequiredExp() - currentLevel.getRequiredExp();
                return expNeeded - currentExp;
        }
}

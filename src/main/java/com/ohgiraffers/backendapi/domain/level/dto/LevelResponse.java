package com.ohgiraffers.backendapi.domain.level.dto;

import com.ohgiraffers.backendapi.domain.level.entity.Level;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LevelResponse {

    private Long levelId;
    private Integer requiredExp;
    private Integer maxCommentLimit;
    private Boolean canUploadImage;

    public static LevelResponse from(Level level) {
        return LevelResponse.builder()
                .levelId(level.getId())
                .requiredExp(level.getRequiredExp())
                .maxCommentLimit(level.getMaxCommentLimit())
                .canUploadImage(level.getCanUploadImage())
                .build();
    }

    /**
     * 레벨업 결과 정보를 담는 DTO
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LevelUpResult {
        private boolean leveledUp;
        private Long previousLevelId;
        private Long currentLevelId;
        private Integer currentExp;
        private Integer nextLevelExp; // 다음 레벨까지 필요한 경험치 (null이면 최고 레벨)

        public static LevelUpResult noChange(Long currentLevelId, Integer currentExp, Integer nextLevelExp) {
            return LevelUpResult.builder()
                    .leveledUp(false)
                    .previousLevelId(currentLevelId)
                    .currentLevelId(currentLevelId)
                    .currentExp(currentExp)
                    .nextLevelExp(nextLevelExp)
                    .build();
        }

        public static LevelUpResult levelUp(Long previousLevelId, Long newLevelId, Integer currentExp,
                Integer nextLevelExp) {
            return LevelUpResult.builder()
                    .leveledUp(true)
                    .previousLevelId(previousLevelId)
                    .currentLevelId(newLevelId)
                    .currentExp(currentExp)
                    .nextLevelExp(nextLevelExp)
                    .build();
        }
    }
}

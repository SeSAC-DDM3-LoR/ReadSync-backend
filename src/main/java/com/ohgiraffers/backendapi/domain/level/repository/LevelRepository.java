package com.ohgiraffers.backendapi.domain.level.repository;

import com.ohgiraffers.backendapi.domain.level.entity.Level;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LevelRepository extends JpaRepository<Level, Long> {

    /**
     * 주어진 경험치로 달성 가능한 최고 레벨을 조회
     * required_exp <= experience 인 레벨 중 가장 높은 레벨
     */
    @Query("SELECT l FROM Level l WHERE l.requiredExp <= :experience ORDER BY l.requiredExp DESC LIMIT 1")
    Optional<Level> findMaxLevelByExperience(@Param("experience") int experience);

    /**
     * 현재 레벨의 다음 레벨 조회
     */
    @Query("SELECT l FROM Level l WHERE l.id = :currentLevelId + 1")
    Optional<Level> findNextLevel(@Param("currentLevelId") Long currentLevelId);

    /**
     * 모든 레벨을 id 순으로 조회
     */
    List<Level> findAllByOrderByIdAsc();
}

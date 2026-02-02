package com.ohgiraffers.backendapi.domain.user.repository;

import com.ohgiraffers.backendapi.domain.user.entity.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {

    /**
     * 유저 ID를 기반으로 취향 정보를 조회합니다.
     * @param userId 유저 고유 ID
     * @return 유저 취향 정보 (Optional)
     */
    Optional<UserPreference> findByUser_Id(Long userId);
}
package com.ohgiraffers.backendapi.domain.credit.repository;

import com.ohgiraffers.backendapi.domain.credit.entity.Credit;
import com.ohgiraffers.backendapi.domain.credit.enums.CreditStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CreditRepository extends JpaRepository<Credit, Long> {

    // 1. 유저의 사용 가능 크레딧을 '만료일 임박순'으로 조회 (차감 로직용)
    List<Credit> findAllByUserIdAndStatusOrderByExpiredAtAsc(Long userId, CreditStatus status);

    // 2. 유저의 현재 총 잔액 계산 (★ 주석 해제됨)
    // 결과가 없을 때 null 대신 0을 반환하도록 COALESCE 사용 권장
    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM Credit c WHERE c.user.id = :userId AND c.status = 'ACTIVE'")
    Integer calculateTotalAmount(@Param("userId") Long userId);

    // [어드민 1] 전체 조회 (최신순 + 유저/타입 Fetch Join으로 성능 최적화)
    @Query("SELECT c FROM Credit c JOIN FETCH c.user u JOIN FETCH u.userInformation JOIN FETCH c.creditType")
    Page<Credit> findAllCredits(Pageable pageable);

    // [어드민 2] 특정 유저 + 날짜 필터링 조회
    @Query("SELECT c FROM Credit c " +
            "JOIN FETCH c.user u JOIN FETCH u.userInformation JOIN FETCH c.creditType " +
            "WHERE c.user.id = :userId " +
            "AND c.createdAt BETWEEN :startDate AND :endDate")
    Page<Credit> findHistoryByUserIdAndDate(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    // [어드민 3] 날짜별 전체 조회 (유저 상관없이 기간 검색)
    @Query("SELECT c FROM Credit c " +
            "JOIN FETCH c.user u JOIN FETCH u.userInformation JOIN FETCH c.creditType " +
            "WHERE c.createdAt BETWEEN :startDate AND :endDate")
    Page<Credit> findAllByDate(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );
}
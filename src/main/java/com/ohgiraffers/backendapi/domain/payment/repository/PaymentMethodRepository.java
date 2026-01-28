package com.ohgiraffers.backendapi.domain.payment.repository;

import com.ohgiraffers.backendapi.domain.payment.entity.PaymentMethod;
import com.ohgiraffers.backendapi.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 결제 수단(빌링키) 엔티티를 관리하는 JPA 리포지토리입니다.
 */
@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {

    /**
     * 특정 유저의 활성화된 결제 수단 목록을 조회합니다 (삭제되지 않은 것).
     */
    List<PaymentMethod> findByUserAndDeletedAtIsNull(User user);

    /**
     * 특정 유저의 기본 결제 수단을 조회합니다.
     */
    // 기본 결제 수단 조회 (중복 방지를 위해 List 반환 후 처리)
    List<PaymentMethod> findByUserAndIsDefaultTrueAndDeletedAtIsNullOrderByCreatedAtDesc(User user);

    // 기존 메서드 유지 (deprecated)
    Optional<PaymentMethod> findByUserAndIsDefaultTrueAndDeletedAtIsNull(User user);
}

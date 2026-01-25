package com.ohgiraffers.backendapi.domain.credit.repository;

import com.ohgiraffers.backendapi.domain.credit.entity.CreditType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CreditTypeRepository extends JpaRepository<CreditType, Long> {
    // 필요한 경우 findByName 등의 메서드를 추가할 수 있습니다.
}
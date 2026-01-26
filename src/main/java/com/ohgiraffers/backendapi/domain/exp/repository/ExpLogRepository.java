package com.ohgiraffers.backendapi.domain.exp.repository;

import com.ohgiraffers.backendapi.domain.exp.entity.ExpLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface ExpLogRepository extends JpaRepository<ExpLog, Long> {

    @EntityGraph(attributePaths = {"expRule"}) // ExpRule을 fetch join하여 N+1 방지
    Page<ExpLog> findAllByUser_Id(Long userId, Pageable pageable);

    // 중복 보상 여부 확인
    boolean existsByUser_IdAndExpRule_ExpRuleIdAndReferenceId(Long userId, Long expRuleId, Long referenceId);
}

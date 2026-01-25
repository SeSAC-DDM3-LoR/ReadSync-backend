package com.ohgiraffers.backendapi.domain.report.repository;

import com.ohgiraffers.backendapi.domain.report.entity.Report;
import com.ohgiraffers.backendapi.domain.report.enums.ReportStatus;
import com.ohgiraffers.backendapi.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findByTargetUserOrderByCreatedAtDesc(User targetUser);


    long countByTargetUser(User targetUser);


    Page<Report> findByStatus(ReportStatus status, Pageable pageable);


    List<Report> findByReporter(User reporter);
}

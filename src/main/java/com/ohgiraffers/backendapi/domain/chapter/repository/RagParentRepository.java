package com.ohgiraffers.backendapi.domain.chapter.repository;

import com.ohgiraffers.backendapi.domain.chapter.entity.RagParentDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RagParentRepository extends JpaRepository<RagParentDocument, Long> {
    List<RagParentDocument> findByChapterId(Long chapterId);
}

package com.ohgiraffers.backendapi.domain.chapter.repository;

import com.ohgiraffers.backendapi.domain.chapter.entity.RagChildVector;
import com.ohgiraffers.backendapi.domain.chapter.entity.RagParentDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RagChildRepository extends JpaRepository<RagChildVector, Long> {
    void deleteByParentIn(List<RagParentDocument> parents);
}

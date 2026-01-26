package com.ohgiraffers.backendapi.domain.chapter.repository;

import com.ohgiraffers.backendapi.domain.chapter.entity.ChapterVectorRag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChapterVectorRagRepository extends JpaRepository<ChapterVectorRag, Long> {
    List<ChapterVectorRag> findByChapter_ChapterId(Long chapterId);
    void deleteByChapter_ChapterId(Long chapterId);
}

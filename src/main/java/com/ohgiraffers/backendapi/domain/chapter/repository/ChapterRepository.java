package com.ohgiraffers.backendapi.domain.chapter.repository;

import com.ohgiraffers.backendapi.domain.chapter.entity.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChapterRepository extends JpaRepository<Chapter, Long> {
    java.util.List<Chapter> findByBook_BookIdOrderBySequenceAsc(Long bookId);
    List<Chapter> findAllByBook_BookId(Long bookId);
}

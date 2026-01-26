package com.ohgiraffers.backendapi.domain.chapter.repository;

import com.ohgiraffers.backendapi.domain.chapter.entity.ChapterVector;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChapterVectorRepository extends JpaRepository<ChapterVector, Long> {

    // 북 벡터 합성을 위해 특정 도서의 모든 챕터 벡터를 가져오는 쿼리
    @NativeQuery(value = "SELECT cv.vector FROM chapter_vectors cv " +
            "JOIN chapters c ON cv.chapter_id = c.chapter_id " +
            "WHERE c.book_id = :bookId")
    List<float[]> findAllVectorsByBookId(@Param("bookId") Long bookId);

    // 만약 챕터 벡터 목록을 API로 노출할 경우를 대비한 페이징 메서드
}

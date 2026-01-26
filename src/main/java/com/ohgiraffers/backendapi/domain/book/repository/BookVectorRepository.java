package com.ohgiraffers.backendapi.domain.book.repository;

import com.ohgiraffers.backendapi.domain.book.entity.BookVector;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface BookVectorRepository extends JpaRepository<BookVector, Long> {

    // 특정 벡터와 유사한 도서 검색 (Pageable 적용)
    // score: 1 - cosine distance (유사할수록 1에 가까움)
    @NativeQuery(value = "SELECT book_id, (1 - (vector <=> cast(:queryVector as halfvec))) as score " +
            "FROM book_vectors " +
            "WHERE (:excludeId IS NULL OR book_id != :excludeId) " +
            "ORDER BY vector <=> cast(:queryVector as halfvec)",
            countQuery = "SELECT count(*) FROM book_vectors WHERE (:excludeId IS NULL OR book_id != :excludeId)")
    Page<Object[]> findSimilarBookIds(
            @Param("queryVector") String queryVector,
            @Param("excludeId") Long excludeId,
            Pageable pageable);
}
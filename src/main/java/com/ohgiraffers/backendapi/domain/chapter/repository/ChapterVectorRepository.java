package com.ohgiraffers.backendapi.domain.chapter.repository;

import com.ohgiraffers.backendapi.domain.chapter.entity.Chapter;
import com.ohgiraffers.backendapi.domain.chapter.entity.ChapterVector;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChapterVectorRepository extends JpaRepository<ChapterVector, Long> {

//    @NativeQuery(value = "SELECT cv.vector::text FROM chapter_vectors cv " +
//            "JOIN chapters c ON cv.chapter_id = c.chapter_id " +
//            "WHERE c.book_id = :bookId")
//    List<String> findAllVectorsByBookId(@Param("bookId") Long bookId); // 반환형을 String으로 변경


    @Query("SELECT cv.vector FROM ChapterVector cv " +
            "JOIN cv.chapter c " +
            "WHERE c.book.bookId = :bookId")
    List<float[]> findAllVectorsByBookId(@Param("bookId") Long bookId);

    Optional<ChapterVector> findByChapter(Chapter chapter);

    @NativeQuery(value = "SELECT ch.book_id, MAX(1 - (cv.vector <=> cast(:vectorString as vector))) as max_score " +
            "FROM chapter_vectors cv " +
            "JOIN chapters ch ON cv.chapter_id = ch.chapter_id " +
            "WHERE (:excludeId IS NULL OR ch.book_id != :excludeId) " +
            "  AND cv.vector IS NOT NULL " + // 벡터가 null인 경우 제외
            "GROUP BY ch.book_id " +
            "HAVING MAX(1 - (cv.vector <=> cast(:vectorString as vector))) IS NOT NULL " + // NaN 필터링
            "   AND MAX(1 - (cv.vector <=> cast(:vectorString as vector))) != 'NaN'::float8 " +
            "ORDER BY max_score DESC")
    Page<Object[]> findSimilarBookIdsByChapters(@Param("vectorString") String vectorString,
                                                @Param("excludeId") Long excludeId,
                                                Pageable pageable);

}

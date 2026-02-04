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
        // 특정 벡터와 유사한 도서 검색 (Pageable 적용)
        // score: 1 - cosine distance (유사할수록 1에 가까움)
        // COALESCE(:excludeIds, NULL) IS NULL 구문은 :excludeIds가 NULL일 때 참이 되어 조건을 무시하는
        // 효과를 냅니다.
        // 하지만 JPA Native Query에서 List 파라미터가 null이면 에러가 날 수 있으므로, Service에서 빈 리스트 대신
        // null을 보낼지 결정해야 합니다.
        // 여기서는 (book_id NOT IN (:excludeIds)) 형태로 처리하되, excludeIds가 비어있으면 쿼리 에러가 날 수
        // 있음.
        // 따라서 Service에서 비어 있다면 -1L을 넣어서 보내는 것이 안전합니다.
        @NativeQuery(value = "SELECT book_id, MAX(1 - (vector <=> cast(:queryVector as halfvec))) as score " +
                        "FROM book_vectors " +
                        "WHERE (:hasExcludes = false OR book_id NOT IN (:excludeIds)) " +
                        "GROUP BY book_id " +
                        "ORDER BY score DESC, book_id ASC", countQuery = "SELECT count(DISTINCT book_id) FROM book_vectors WHERE (:hasExcludes = false OR book_id NOT IN (:excludeIds))")
        Page<Object[]> findSimilarBookIds(
                        @Param("queryVector") String queryVector,
                        @Param("excludeIds") List<Long> excludeIds,
                        @Param("hasExcludes") boolean hasExcludes,
                        Pageable pageable);
}
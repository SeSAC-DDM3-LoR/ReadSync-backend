package com.ohgiraffers.backendapi.domain.chapter.repository;

import com.ohgiraffers.backendapi.domain.chapter.entity.RagChildVector;
import com.ohgiraffers.backendapi.domain.chapter.entity.RagParentDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RagChildRepository extends JpaRepository<RagChildVector, Long> {
    void deleteByParentIn(List<RagParentDocument> parents);

    // nativeQuery를 사용하여 vector 연산자(<=>) 수행
    // CAST(:queryVector AS halfvec) 필요 (String으로 넘길 경우)
    @Query(value = """
            SELECT c.*
            FROM rag_child_vectors c
            JOIN rag_parent_documents p ON c.parent_id = p.parent_id
            WHERE p.chapter_id = :chapterId
            ORDER BY c.vector <=> CAST(:queryVector AS halfvec)
            LIMIT 5
            """, nativeQuery = true)
    List<RagChildVector> findTop5ByVectorSimilarity(@Param("chapterId") Long chapterId,
            @Param("queryVector") String queryVector);
}

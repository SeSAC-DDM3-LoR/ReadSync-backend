package com.ohgiraffers.backendapi.domain.library.repository;

import com.ohgiraffers.backendapi.domain.library.entity.Library;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface LibraryRepository extends JpaRepository<Library, Long> {

    // 유저의 전체 서재 목록 페이징 조회
    Page<Library> findAllByUserIdAndDeletedAtIsNull(Long userId, Pageable pageable);

    // 유저의 서재 내 특정 카테고리 도서 페이징 조회
    Page<Library> findAllByUserIdAndBook_Category_CategoryIdAndDeletedAtIsNull(Long userId, Long categoryId,
            Pageable pageable);

    // 해당 책을 이미 소유하고 있는지 확인
    boolean existsByUserIdAndBook_BookId(Long userId, Long bookId);

    // 유저가 소유한(삭제되지 않은) 모든 도서 ID 조회 (추천 필터링용)
    @Query("SELECT l.book.bookId FROM Library l WHERE l.user.id = :userId AND l.deletedAt IS NULL")
    List<Long> findBookIdsByUserId(@Param("userId") Long userId);

    // 유저의 활성 서재 아이템 중 특정 진행률(마일스톤 30%) 이상인 책만 카운트 (부스팅 로직용)
    long countByUserIdAndDeletedAtIsNullAndTotalProgressGreaterThanEqual(Long userId, BigDecimal progress);
}

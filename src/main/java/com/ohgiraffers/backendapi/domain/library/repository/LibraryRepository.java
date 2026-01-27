package com.ohgiraffers.backendapi.domain.library.repository;

import com.ohgiraffers.backendapi.domain.library.entity.Library;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LibraryRepository extends JpaRepository<Library, Long> {

    // 유저의 전체 서재 목록 페이징 조회
    Page<Library> findAllByUserIdAndDeletedAtIsNull(Long userId, Pageable pageable);

    // 유저의 서재 내 특정 카테고리 도서 페이징 조회
    Page<Library> findAllByUserIdAndBook_Category_CategoryIdAndDeletedAtIsNull(Long userId, Long categoryId,
            Pageable pageable);

    // 해당 책을 이미 소유하고 있는지 확인
    boolean existsByUserIdAndBook_BookId(Long userId, Long bookId);
}

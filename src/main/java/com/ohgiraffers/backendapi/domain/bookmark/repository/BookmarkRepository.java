package com.ohgiraffers.backendapi.domain.bookmark.repository;

import com.ohgiraffers.backendapi.domain.bookmark.entity.Bookmark;
import com.ohgiraffers.backendapi.domain.library.entity.Library;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    Optional<Bookmark> findByLibrary_LibraryIdAndChapter_ChapterId(Long libraryId, Long chapterId);

    // [Fix] 동시성 제어를 위한 비관적 락 (Pessimistic Lock) 적용
    // "페이지 넘김 저장" 기능으로 인한 Race Condition 및 Lost Update 방지
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Bookmark b WHERE b.library.libraryId = :libraryId AND b.chapter.chapterId = :chapterId")
    Optional<Bookmark> findByLibraryAndChapterWithLock(@Param("libraryId") Long libraryId,
            @Param("chapterId") Long chapterId);

    // 특정 서재의 북마크 목록 페이징 조회
    Page<Bookmark> findAllByLibrary(Library library, Pageable pageable);

    // 사용자의 모든 북마크 조회 (Library를 거쳐 User ID로 조회)
    Page<Bookmark> findAllByLibrary_User_Id(Long userId, Pageable pageable);
}

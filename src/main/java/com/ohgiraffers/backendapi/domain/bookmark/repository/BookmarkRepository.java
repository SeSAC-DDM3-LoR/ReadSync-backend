package com.ohgiraffers.backendapi.domain.bookmark.repository;

import com.ohgiraffers.backendapi.domain.bookmark.entity.Bookmark;
import com.ohgiraffers.backendapi.domain.library.entity.Library;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    Optional<Bookmark> findByLibrary_LibraryIdAndChapter_ChapterId(Long libraryId, Long chapterId);

    // 특정 서재의 북마크 목록 페이징 조회
    Page<Bookmark> findAllByLibrary(Library library, Pageable pageable);

    // 사용자의 모든 북마크 조회 (Library를 거쳐 User ID로 조회)
    Page<Bookmark> findAllByLibrary_User_Id(Long userId, Pageable pageable);
}

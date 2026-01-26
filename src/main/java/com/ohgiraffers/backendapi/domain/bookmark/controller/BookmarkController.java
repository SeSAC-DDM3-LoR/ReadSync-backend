package com.ohgiraffers.backendapi.domain.bookmark.controller;

import com.ohgiraffers.backendapi.domain.booklog.dto.BookLogResponseDTO;
import com.ohgiraffers.backendapi.domain.bookmark.dto.BookmarkRequestDTO;
import com.ohgiraffers.backendapi.domain.bookmark.dto.BookmarkResponseDTO;
import com.ohgiraffers.backendapi.domain.bookmark.entity.Bookmark;
import com.ohgiraffers.backendapi.domain.bookmark.service.BookmarkService;
import com.ohgiraffers.backendapi.global.common.annotation.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "bookmark", description = "북마크 관리 API")
@RestController
@RequestMapping("/v1/bookmarks")
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @Operation(summary = "[공통] 북마크 생성 및 수정", description = "읽기 상태를 동기화하거나 새로운 북마크를 생성합니다.")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @PostMapping
    public ResponseEntity<Long> saveOrUpdate(@RequestBody BookmarkRequestDTO requestDTO) {
        return ResponseEntity.ok(bookmarkService.saveOrUpdate(requestDTO));
    }

    @Operation(summary = "[공통] 북마크 단건 조회", description = "특정 북마크의 상세 정보를 조회합니다.")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping("/{bookmarkId}")
    public ResponseEntity<BookmarkResponseDTO> getBookmark(@PathVariable Long bookmarkId) {
        return ResponseEntity.ok(bookmarkService.getBookmark(bookmarkId));
    }

    @Operation(summary = "[사용자] 내 북마크 목록 조회 (페이징)", description = "현재 로그인한 사용자의 모든 북마크를 페이징하여 가져옵니다.")
    @PreAuthorize("hasRole('USER')")
    @GetMapping("/me")
    public ResponseEntity<Page<BookmarkResponseDTO>> getMyBookmark(
            @CurrentUserId Long userId,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(bookmarkService.findAllByUser(userId, pageable));
    }

    @Operation(summary = "[공통] 특정 서재의 모든 북마크 조회 (페이징)", description = "지정한 서재(도서)에 포함된 모든 북마크 목록을 가져옵니다.")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping("/library/{libraryId}")
    public ResponseEntity<Page<BookmarkResponseDTO>> getLibraryBookmarks(
            @PathVariable Long libraryId,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(bookmarkService.getBookmarksByLibrary(libraryId, pageable));
    }

    @Operation(summary = "[공통] 북마크 삭제", description = "특정 북마크를 삭제 처리합니다.")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @DeleteMapping("/{bookmarkId}")
    public ResponseEntity<Void> deleteBookmark(@PathVariable Long bookmarkId) {
        bookmarkService.deleteBookmark(bookmarkId);
        return ResponseEntity.noContent().build();
    }
}

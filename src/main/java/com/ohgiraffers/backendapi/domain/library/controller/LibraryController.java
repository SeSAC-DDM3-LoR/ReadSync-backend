package com.ohgiraffers.backendapi.domain.library.controller;

import com.ohgiraffers.backendapi.domain.library.dto.LibraryRequestDTO;
import com.ohgiraffers.backendapi.domain.library.dto.LibraryResponseDTO;
import com.ohgiraffers.backendapi.domain.library.enums.ReadingStatus;
import com.ohgiraffers.backendapi.domain.library.service.LibraryService;
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

@Tag(name = "my-library", description = "개인 서재(내 서재) 관리 API")
@RestController
@RequestMapping("/v1/my-library")
@RequiredArgsConstructor
public class LibraryController {
    private final LibraryService libraryService;

    @Operation(summary = "[공통] 서재에 도서 추가", description = "특정 도서를 내 서재에 담습니다.")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @PostMapping("/me")
    public ResponseEntity<Long> addToMyLibrary(@CurrentUserId Long userId, @RequestBody LibraryRequestDTO request) {
        return ResponseEntity.ok(libraryService.addToLibrary(userId, request));
    }

    @Operation(summary = "[관리자] 서재에 도서 추가", description = "특정 도서를 유저 서재에 담습니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{userId}")
    public ResponseEntity<Long> addToOtherLibrary(@PathVariable Long userId, @RequestBody LibraryRequestDTO request) {
        return ResponseEntity.ok(libraryService.addToLibrary(userId, request));
    }

    @Operation(summary = "[공통] 타 유저 서재 조회 (페이징)", description = "특정 유저의 서재 목록을 조회합니다.")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<LibraryResponseDTO>> getUserLibrary(
            @PathVariable Long userId,
            @PageableDefault(size = 12, sort = "libraryId") Pageable pageable) {
        return ResponseEntity.ok(libraryService.getUserLibrary(userId, pageable));
    }

    @Operation(summary = "[사용자] 내 서재 조회 (페이징)", description = "현재 로그인한 사용자의 서재 목록을 조회합니다.")
    @PreAuthorize("hasRole('USER')")
    @GetMapping("/me")
    public ResponseEntity<Page<LibraryResponseDTO>> getMyBookLog(
            @CurrentUserId Long userId,
            @PageableDefault(size = 12, sort = "libraryId") Pageable pageable) {
        return ResponseEntity.ok(libraryService.getUserLibrary(userId, pageable));
    }

//    시스템 내부적으로 사용하기 때문에 주석 처리
//    @Operation(summary = "[사용자] 독서 상태 변경", description = "서재에 담긴 도서의 읽기 상태(읽는 중, 완독 등)를 변경합니다.")
//    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
//    @PatchMapping("/{libraryId}/status")
//    public ResponseEntity<Void> updateStatus(
//            @PathVariable Long libraryId,
//            @RequestParam ReadingStatus status) {
//        libraryService.updateReadingStatus(libraryId, status);
//        return ResponseEntity.ok().build();
//    }

    @Operation(summary = "[공통] 서재 내 카테고리별 조회 (페이징)", description = "유저의 서재에서 특정 카테고리의 도서만 필터링하여 조회합니다.")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping("/user/{userId}/category/{categoryId}")
    public ResponseEntity<Page<LibraryResponseDTO>> getLibraryByCategory(
            @PathVariable Long userId,
            @PathVariable Long categoryId,
            @PageableDefault(size = 12) Pageable pageable) {
        return ResponseEntity.ok(libraryService.getLibraryByUserIdAndCategoryId(userId, categoryId, pageable));
    }

    @Operation(summary = "[공통] 서재에서 도서 삭제", description = "내 서재에서 도서를 제거(Soft Delete)합니다.")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @DeleteMapping("/{libraryId}")
    public ResponseEntity<Void> delete(@PathVariable Long libraryId) {
        libraryService.deleteFromLibrary(libraryId);
        return ResponseEntity.noContent().build();
    }
}

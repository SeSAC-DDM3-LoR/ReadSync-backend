package com.ohgiraffers.backendapi.domain.book.controller;

import com.ohgiraffers.backendapi.domain.book.dto.BookRecommendationDTO;
import com.ohgiraffers.backendapi.domain.book.dto.BookVectorDTO;
import com.ohgiraffers.backendapi.domain.book.service.BookVectorService;
import com.ohgiraffers.backendapi.global.common.annotation.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "book-vector", description = "도서 벡터 및 추천 API")
@RestController
@RequestMapping("/v1/book-vectors")
@RequiredArgsConstructor
public class BookVectorController {

    private final BookVectorService bookVectorService;

    @Operation(summary = "[관리자] 도서 벡터 생성", description = "챕터 데이터를 기반으로 특정 도서의 통합 벡터를 생성하거나 갱신합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{bookId}/generate")
    public ResponseEntity<String> generateBookVector(
            @Parameter(description = "대상 도서 고유 번호") @PathVariable Long bookId) {
        bookVectorService.createBookVectorFromChapters(bookId);
        return ResponseEntity.ok("도서 ID " + bookId + "의 벡터가 성공적으로 생성되었습니다.");
    }

    @Operation(summary = "[공통] 유사 도서 추천 (페이징)", description = "특정 도서와 벡터 유사도가 높은 추천 도서 목록을 페이징하여 가져옵니다.")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping("/recommend/{bookId}")
    public ResponseEntity<Page<BookRecommendationDTO>> getSimilarBooks(
            @Parameter(description = "기준 도서 고유 번호") @PathVariable Long bookId,
            @Parameter(hidden = true) @PageableDefault(size = 5) Pageable pageable) {

        Page<BookRecommendationDTO> recommendations = bookVectorService.getRecommendationsByBookId(bookId, pageable);
        return ResponseEntity.ok(recommendations);
    }

    @Operation(summary = "[공통] 취향 벡터 기반 추천 (페이징)", description = "사용자의 취향 벡터를 입력받아 유사한 도서 목록을 검색합니다.")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping("/search")
    public ResponseEntity<Page<BookRecommendationDTO>> searchByVector(
            @CurrentUserId Long userId,
            @Parameter(hidden = true) @PageableDefault(size = 5) Pageable pageable) {

        Page<BookRecommendationDTO> results = bookVectorService.getRecommendationsByVector(userId, pageable);
        return ResponseEntity.ok(results);
    }

    @Operation(summary = "[공통] 자유 텍스트 기반 추천", description = "입력한 문장과 가장 유사한 주제의 도서를 추천합니다.")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping("/search-text")
    public ResponseEntity<Page<BookRecommendationDTO>> searchByText(
            @RequestParam String text,
            @Parameter(hidden = true) @PageableDefault(size = 5) Pageable pageable) {

        return ResponseEntity.ok(bookVectorService.getRecommendationsByText(text, pageable));
    }

    /**
     * 특정 도서의 모든 챕터를 임베딩하고 최적화된 북 벡터 생성
     */
    @Operation(summary = "[관리자] 도서 벡터 일괄 생성/갱신", description = "특정 도서의 모든 챕터 링크를 파이썬으로 보내 임베딩하고, 최적화된 알고리즘으로 북 벡터를 생성합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/process/{bookId}")
    public ResponseEntity<String> processBookEmbedding(
            @Parameter(description = "벡터화할 도서 고유 번호") @PathVariable Long bookId) {

        bookVectorService.processFullBookEmbedding(bookId);
        return ResponseEntity.ok("성공적으로 도서 ID " + bookId + "의 벡터 처리를 완료했습니다.");
    }
}
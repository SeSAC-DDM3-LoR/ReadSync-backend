package com.ohgiraffers.backendapi.domain.book.controller;

import com.ohgiraffers.backendapi.domain.book.dto.BookRequestDTO;
import com.ohgiraffers.backendapi.domain.book.dto.BookResponseDTO;
import com.ohgiraffers.backendapi.domain.book.service.BookService;
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

import java.util.List;

@Tag(name = "book", description = "도서 관리 API")
@RestController
@RequestMapping("/v1/books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;

    @Operation(summary = "[관리자] 도서 등록", description = "새로운 도서 정보를 등록합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<Long> createBook(@RequestBody BookRequestDTO request) {
        return ResponseEntity.ok(bookService.createBook(request));
    }

    @Operation(summary = "[공통] 도서 단건 조회", description = "ID를 통해 특정 도서의 상세 정보를 조회합니다.")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping("/{bookId}")
    public ResponseEntity<BookResponseDTO> getBook(
            @Parameter(description = "도서 고유 번호") @PathVariable Long bookId) {
        return ResponseEntity.ok(bookService.getBook(bookId));
    }

    @Operation(summary = "[공통] 도서 목록 전체 조회", description = "등록된 모든 도서 목록을 페이징하여 가져옵니다.")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping
    public ResponseEntity<Page<BookResponseDTO>> getAllBooks(
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(bookService.getAllBooks(pageable));
    }

    @Operation(summary = "[관리자] 도서 정보 수정", description = "기존 도서의 정보를 수정합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{bookId}")
    public ResponseEntity<String> updateBook(
            @PathVariable Long bookId,
            @RequestBody BookRequestDTO request) {
        bookService.updateBook(bookId, request);
        return ResponseEntity.ok("도서 정보가 성공적으로 수정되었습니다.");
    }

    @Operation(summary = "[관리자] 도서 삭제", description = "특정 도서를 시스템에서 제거합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{bookId}")
    public ResponseEntity<Void> deleteBook(@PathVariable Long bookId) {
        bookService.deleteBook(bookId);
        return ResponseEntity.noContent().build();
    }

    // --- 추가 제안 엔드포인트 ---

    @Operation(summary = "[공통] 도서 검색 페이징", description = "키워드로 도서를 검색하고 페이징 결과를 반환합니다.")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping("/search")
    public ResponseEntity<Page<BookResponseDTO>> searchBooks(
            @RequestParam(name = "keyword") String keyword,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(bookService.searchBooks(keyword, pageable));
    }
}
package com.ohgiraffers.backendapi.domain.category.controller;

import com.ohgiraffers.backendapi.domain.category.dto.CategoryRequestDTO;
import com.ohgiraffers.backendapi.domain.category.dto.CategoryResponseDTO;
import com.ohgiraffers.backendapi.domain.category.service.CategoryService;
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

@Tag(name = "category", description = "카테고리 관리 API")
@RestController
@RequestMapping("/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "[관리자] 카테고리 등록", description = "새로운 도서 카테고리를 등록합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<Long> createCategory(@RequestBody CategoryRequestDTO request) {
        return ResponseEntity.ok(categoryService.createCategory(request));
    }

    @Operation(summary = "[공통] 카테고리 단건 조회", description = "ID를 통해 특정 카테고리 상세 정보를 조회합니다.")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping("/{categoryId}")
    public ResponseEntity<CategoryResponseDTO> getCategory(@PathVariable Long categoryId) {
        return ResponseEntity.ok(categoryService.getCategory(categoryId));
    }

    @Operation(summary = "[공통] 카테고리 목록 조회 (페이징)", description = "삭제되지 않은 모든 카테고리 목록을 페이징하여 조회합니다.")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping
    public ResponseEntity<Page<CategoryResponseDTO>> getAllCategories(
            @PageableDefault(size = 20, sort = "categoryId") Pageable pageable) {
        return ResponseEntity.ok(categoryService.getAllCategories(pageable));
    }

    @Operation(summary = "[관리자] 카테고리 수정", description = "기존 카테고리의 이름을 수정합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{categoryId}")
    public ResponseEntity<String> updateCategory(
            @PathVariable Long categoryId,
            @RequestBody CategoryRequestDTO request) {

        categoryService.updateCategory(categoryId, request);
        return ResponseEntity.ok("카테고리 수정이 완료되었습니다.");
    }

    @Operation(summary = "[관리자] 카테고리 삭제 (Soft)", description = "카테고리를 삭제 처리(Soft Delete)합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long categoryId) {
        categoryService.deleteCategory(categoryId);
        return ResponseEntity.noContent().build();
    }
}

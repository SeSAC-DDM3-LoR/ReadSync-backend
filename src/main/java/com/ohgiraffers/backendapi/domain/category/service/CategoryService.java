package com.ohgiraffers.backendapi.domain.category.service;

import com.ohgiraffers.backendapi.domain.category.dto.CategoryRequestDTO;
import com.ohgiraffers.backendapi.domain.category.dto.CategoryResponseDTO;
import com.ohgiraffers.backendapi.domain.category.entity.Category;
import com.ohgiraffers.backendapi.domain.category.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional
    public Long createCategory(CategoryRequestDTO request) {
        Category category = request.toEntity();
        return categoryRepository.save(category).getCategoryId();
    }

    public CategoryResponseDTO getCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException("해당 카테고리를 찾을 수 없습니다."));
        return CategoryResponseDTO.from(category);
    }

    // 페이징 적용된 카테고리 목록 조회
    public Page<CategoryResponseDTO> getAllCategories(Pageable pageable) {
        return categoryRepository.findAllByDeletedAtIsNull(pageable)
                .map(CategoryResponseDTO::from);
    }

    @Transactional
    public void updateCategory(Long categoryId, CategoryRequestDTO request) {
        Category category = categoryRepository.findById(categoryId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException("수정할 카테고리가 존재하지 않거나 이미 삭제되었습니다."));

        category.update(request);
    }

    @Transactional
    public void deleteCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("이미 존재하지 않는 카테고리입니다."));
        category.delete();
    }
}
package com.ohgiraffers.backendapi.domain.category.repository;

import com.ohgiraffers.backendapi.domain.category.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByCategoryId(Long categoryId);

    Page<Category> findAllByDeletedAtIsNull(Pageable pageable);
}

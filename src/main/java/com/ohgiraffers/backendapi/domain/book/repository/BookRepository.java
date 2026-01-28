package com.ohgiraffers.backendapi.domain.book.repository;

import com.ohgiraffers.backendapi.domain.book.entity.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ohgiraffers.backendapi.domain.order.enums.OrderStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {
    Optional<Book> findByBookId(Long bookId);

    Page<Book> findAllByDeletedAtIsNull(Pageable pageable);

    Page<Book> findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase(String titleKeyword, String authorKeyword,
            Pageable pageable);

    @Query("SELECT oi.book FROM OrderItem oi " +
            "JOIN oi.order o " +
            "WHERE o.user.id = :userId " +
            "AND o.status = :orderStatus " +
            "ORDER BY o.createdAt DESC")
    List<Book> findPurchasedBooks(@Param("userId") Long userId, @Param("orderStatus") OrderStatus orderStatus);
}

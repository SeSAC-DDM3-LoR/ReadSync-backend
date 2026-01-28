package com.ohgiraffers.backendapi.domain.book.dto;

import com.ohgiraffers.backendapi.domain.book.entity.Book;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookRecommendationDTO {
    private Long bookId;
    private String title;
    private String author;
    private String coverUrl;
    private String summary;
    private Double similarityScore; // 검색 쿼리에서 계산된 점수

    public static BookRecommendationDTO from(Book book, Double score) {
        return BookRecommendationDTO.builder()
                .bookId(book.getBookId())
                .title(book.getTitle())
                .author(book.getAuthor())
                .coverUrl(book.getCoverUrl())
                .summary(book.getSummary())
                .similarityScore(score)
                .build();
    }
}
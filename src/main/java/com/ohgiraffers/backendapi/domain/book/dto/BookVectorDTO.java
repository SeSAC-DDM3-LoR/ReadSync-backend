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
public class BookVectorDTO {
    private Long bookId;
    private float[] embedding;

    public static BookVectorDTO from(Book book) {
        return BookVectorDTO.builder()
                .bookId(book.getBookId())
                .embedding(new float[]{})
                .build();
    }
}
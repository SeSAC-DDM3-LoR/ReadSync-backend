package com.ohgiraffers.backendapi.domain.library.service;

import com.ohgiraffers.backendapi.domain.book.entity.Book;
import com.ohgiraffers.backendapi.domain.book.repository.BookRepository;
import com.ohgiraffers.backendapi.domain.exp.annotation.AwardExp;
import com.ohgiraffers.backendapi.domain.exp.enums.ActivityType;
import com.ohgiraffers.backendapi.domain.library.dto.LibraryRequestDTO;
import com.ohgiraffers.backendapi.domain.library.dto.LibraryResponseDTO;
import com.ohgiraffers.backendapi.domain.library.entity.Library;
import com.ohgiraffers.backendapi.domain.library.enums.ReadingStatus;
import com.ohgiraffers.backendapi.domain.library.repository.LibraryRepository;
import com.ohgiraffers.backendapi.domain.user.entity.User;
import com.ohgiraffers.backendapi.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LibraryService {
    private final LibraryRepository libraryRepository;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;

    @Transactional
    public Long addToLibrary(LibraryRequestDTO request) {
        User user = userRepository.findById(request.getUserId())
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        Book book = bookRepository.findById(request.getBookId())
                .filter(b -> b.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 도서입니다."));

        Library library = request.toEntity(user, book);
        return libraryRepository.save(library).getLibraryId();
    }

    // 페이징 적용: 특정 유저의 서재 조회
    public Page<LibraryResponseDTO> getUserLibrary(Long userId, Pageable pageable) {
        return libraryRepository.findAllByUserIdAndDeletedAtIsNull(userId, pageable)
                .map(LibraryResponseDTO::from);
    }

    @AwardExp(type = ActivityType.READ_BOOK)
    @Transactional
    public void updateReadingStatus(Long libraryId, ReadingStatus status) {
        Library library = libraryRepository.findById(libraryId)
                .filter(l -> l.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException("서재 정보를 찾을 수 없습니다."));
        library.updateStatus(status);
    }

    // 페이징 적용: 카테고리별 서재 조회
    public Page<LibraryResponseDTO> getLibraryByUserIdAndCategoryId(Long userId, Long categoryId, Pageable pageable) {
        return libraryRepository
                .findAllByUserIdAndBook_Category_CategoryIdAndDeletedAtIsNull(userId, categoryId, pageable)
                .map(LibraryResponseDTO::from);
    }

    @Transactional
    public void deleteFromLibrary(Long libraryId) {
        Library library = libraryRepository.findById(libraryId)
                .orElseThrow(() -> new IllegalArgumentException("이미 삭제된 정보입니다."));
        library.delete();
    }
}

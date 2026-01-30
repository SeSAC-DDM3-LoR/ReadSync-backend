package com.ohgiraffers.backendapi.domain.library.service;

import com.ohgiraffers.backendapi.domain.book.entity.Book;
import com.ohgiraffers.backendapi.domain.book.repository.BookRepository;
import com.ohgiraffers.backendapi.domain.chapter.repository.ChapterRepository;
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
    public Long addToLibrary(Long userId, LibraryRequestDTO request) {
        User user = userRepository.findById(userId)
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

    @AwardExp(type = ActivityType.READ_BOOK)
    public Library syncOverallProgress(Long libraryId, int newlyReadCount) {
        // 1. 서재와 연결된 도서(Book) 정보를 가져옵니다.
        Library library = libraryRepository.findById(libraryId)
                .orElseThrow(() -> new IllegalArgumentException("서재를 찾을 수 없습니다."));

        Integer totalParagraphs = library.getBook().getTotalParagraphs();

        if (totalParagraphs == 0) {
            return null;
        }

        library.incrementReadCount(newlyReadCount);

        // 4. 최종 진행률 계산
        double overallProgress = (double) library.getTotalReadParagraphs() / totalParagraphs * 100;

        // 5. Library 엔티티 업데이트
        double clampedProgress = Math.min(100.0, Math.max(0.0, overallProgress));

        if (clampedProgress >= 100.0) {
            updateReadingStatus(libraryId, ReadingStatus.COMPLETED);
        }
        library.updateOverallProgress(clampedProgress);

        return library;
    }

    public void saveLastChapter(Long libraryId, Long chapterId) {
        Library library = libraryRepository.findById(libraryId)
                .orElseThrow(() -> new IllegalArgumentException("서재를 찾을 수 없습니다."));
        library.updateLastReadChapter(chapterId);
    }
}

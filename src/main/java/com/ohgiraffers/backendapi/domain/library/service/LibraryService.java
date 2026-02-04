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

    // 단건 조회: 특정 libraryId로 조회
    public LibraryResponseDTO getLibraryById(Long libraryId) {
        Library library = libraryRepository.findById(libraryId)
                .filter(l -> l.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException("서재 정보를 찾을 수 없습니다."));
        return LibraryResponseDTO.from(library);
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

    @Transactional // ReadingEventListener에서 트랜잭션 관리
    @AwardExp(type = ActivityType.READ_BOOK)
    public Library syncOverallProgress(Long libraryId, int newlyReadCount) {
        // 1. 서재와 연결된 도서(Book) 정보를 가져옵니다.
        Library library = libraryRepository.findById(libraryId)
                .orElseThrow(() -> new IllegalArgumentException("서재를 찾을 수 없습니다."));

        Integer totalParagraphs = library.getBook().getTotalParagraphs();

        if (totalParagraphs == null || totalParagraphs == 0) {
            // 분모가 0이면 진행률 계산 불가하지만, 읽은 횟수는 저장해야 함
            // 총 문단 수를 1로 가정하여 최소한의 계산 수행 (Infinity 방지)
            totalParagraphs = 1;
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

        // 6. 마일스톤 체크 (30%, 70%, 100%) 및 중복 업데이트 방지
        int currentMilestone = 0;
        int lastStep = library.getLastVectorUpdateStep();

        // 높은 마일스톤부터 체크해야 급격한 진행률 상승 시에도 가장 중요한 단계를 포착함
        if (lastStep < 100 && clampedProgress >= 100)
            currentMilestone = 100;
        else if (lastStep < 70 && clampedProgress >= 70)
            currentMilestone = 70;
        else if (lastStep < 30 && clampedProgress >= 30)
            currentMilestone = 30;

        if (currentMilestone > 0) {
            library.updateVectorUpdateStep(currentMilestone);
            library.setReachedMilestone(currentMilestone);
        }

        return library;
    }

    @Transactional // ReadingEventListener에서 트랜잭션 관리
    public void saveLastChapter(Long libraryId, Long chapterId) {
        Library library = libraryRepository.findById(libraryId)
                .orElseThrow(() -> new IllegalArgumentException("서재를 찾을 수 없습니다."));
        library.updateLastReadChapter(chapterId);
    }
}

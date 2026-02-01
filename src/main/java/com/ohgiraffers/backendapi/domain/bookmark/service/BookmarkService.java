package com.ohgiraffers.backendapi.domain.bookmark.service;

import com.ohgiraffers.backendapi.domain.booklog.service.BookLogService;
import com.ohgiraffers.backendapi.domain.bookmark.dto.BookmarkRequestDTO;
import com.ohgiraffers.backendapi.domain.bookmark.dto.BookmarkResponseDTO;
import com.ohgiraffers.backendapi.domain.bookmark.entity.Bookmark;
import com.ohgiraffers.backendapi.domain.bookmark.repository.BookmarkRepository;
import com.ohgiraffers.backendapi.domain.chapter.entity.Chapter;
import com.ohgiraffers.backendapi.domain.chapter.repository.ChapterRepository;
import com.ohgiraffers.backendapi.domain.exp.annotation.AwardExp;
import com.ohgiraffers.backendapi.domain.exp.enums.ActivityType;
import com.ohgiraffers.backendapi.domain.library.entity.Library;
import com.ohgiraffers.backendapi.domain.library.enums.ReadingStatus;
import com.ohgiraffers.backendapi.domain.library.repository.LibraryRepository;
import com.ohgiraffers.backendapi.domain.library.service.LibraryService;
import com.ohgiraffers.backendapi.domain.user.service.UserPreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final LibraryService libraryService;
    private final LibraryRepository libraryRepository;
    private final ChapterRepository chapterRepository;
    private final UserPreferenceService userPreferenceService;
    private final BookLogService bookLogService;

    private final Map<String, Integer> pendingCounts = new ConcurrentHashMap<>();

    // @Transactional // ReadingEventListener에서 트랜잭션 관리
    public BookmarkUpdateResult saveOrUpdate(BookmarkRequestDTO dto) {
        // ... 기존 로직 유지 (Chapter 조회, Bookmark 확보, syncReadStatus 등)
        Chapter chapter = chapterRepository.findById(dto.getChapterId())
                .orElseThrow(() -> new IllegalArgumentException("챕터가 없습니다."));

        Bookmark bookmark = bookmarkRepository
                .findByLibrary_LibraryIdAndChapter_ChapterId(dto.getLibraryId(), dto.getChapterId())
                .orElseGet(() -> {
                    Library library = libraryRepository.findById(dto.getLibraryId())
                            .orElseThrow(() -> new IllegalArgumentException("서재를 찾을 수 없습니다."));

                    if (!Objects.equals(library.getBook().getBookId(), chapter.getBook().getBookId())) {
                        throw new IllegalArgumentException("해당 도서의 챕터가 아닙니다.");
                    }

                    byte[] maskBytes = "0".repeat(chapter.getParagraphs()).getBytes(StandardCharsets.UTF_8);
                    libraryService.updateReadingStatus(library.getLibraryId(), ReadingStatus.READING);
                    return bookmarkRepository.save(dto.toEntity(library, chapter, maskBytes));
                });

        int newlyReadCount = bookmark.syncReadStatus(dto.getReadParagraphIndices(), dto.getLastReadPos());

        // [수정] 취향 분석에 필요한 정보를 함께 리턴
        return new BookmarkUpdateResult(newlyReadCount, chapter.getParagraphs());
    }

    public record BookmarkUpdateResult(int newlyReadCount, int chapterParagraphs) {
    }

    @Transactional(readOnly = true)
    public BookmarkResponseDTO getBookmark(Long bookmarkId) {
        Bookmark bookmark = bookmarkRepository.findById(bookmarkId)
                .orElseThrow(() -> new IllegalArgumentException("북마크 정보를 찾을 수 없습니다."));
        return BookmarkResponseDTO.from(bookmark);
    }

    // 내 북마크 전체 조회 (페이징)
    @Transactional(readOnly = true)
    public Page<BookmarkResponseDTO> findAllByUser(Long userId, Pageable pageable) {
        return bookmarkRepository.findAllByLibrary_User_Id(userId, pageable)
                .map(BookmarkResponseDTO::from);
    }

    // 특정 서재의 북마크 조회 (페이징)
    @Transactional(readOnly = true)
    public Page<BookmarkResponseDTO> getBookmarksByLibrary(Long libraryId, Pageable pageable) {
        Library library = libraryRepository.findById(libraryId)
                .orElseThrow(() -> new IllegalArgumentException("서재 정보를 찾을 수 없습니다."));

        return bookmarkRepository.findAllByLibrary(library, pageable)
                .map(BookmarkResponseDTO::from);
    }

    @Transactional
    public void deleteBookmark(Long bookmarkId) {
        Bookmark bookmark = bookmarkRepository.findById(bookmarkId)
                .orElseThrow(() -> new IllegalArgumentException("북마크 정보를 찾을 수 없습니다."));
        bookmark.delete(); // Soft Delete 또는 로직 처리
    }
}
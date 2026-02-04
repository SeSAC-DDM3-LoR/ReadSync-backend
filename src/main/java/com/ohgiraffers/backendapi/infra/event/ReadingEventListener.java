package com.ohgiraffers.backendapi.infra.event;

import com.ohgiraffers.backendapi.domain.bookmark.dto.BookmarkRequestDTO;
import com.ohgiraffers.backendapi.domain.bookmark.service.BookmarkService;
import com.ohgiraffers.backendapi.domain.booklog.dto.BookLogRequestDTO;
import com.ohgiraffers.backendapi.domain.booklog.service.BookLogService;
import com.ohgiraffers.backendapi.domain.library.entity.Library;
import com.ohgiraffers.backendapi.domain.library.service.LibraryService;
import com.ohgiraffers.backendapi.domain.user.service.UserPreferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReadingEventListener {

    private final BookmarkService bookmarkService;
    private final LibraryService libraryService;
    private final UserPreferenceService userPreferenceService;
    private final BookLogService bookLogService;

    // private final Map<String, Integer> pendingCounts = new ConcurrentHashMap<>();

    @EventListener
    @Transactional // 모든 저장 작업을 하나의 트랜잭션으로 묶음
    public void handleReadingEvent(ReadingEvent event) {

        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║              📚 독서 펄스 이벤트 수신                          ║");
        log.info("╠══════════════════════════════════════════════════════════════╣");
        log.info("║ 👤 userId:        {}", event.getUserId());
        log.info("║ 📖 libraryId:     {}", event.getLibraryId());
        log.info("║ 📄 chapterId:     {}", event.getChapterId());
        log.info("║ 📍 lastReadPos:   {}", event.getLastReadPos());
        log.info("║ 📑 readParagraphs: {}", event.getReadParagraphIndices());
        log.info("║ ⏱️ readTime(초):   {}", event.getReadTime());
        log.info("╚══════════════════════════════════════════════════════════════╝");

        try {
            // [1] 북마크 저장
            log.info("┌─[STEP 1] 북마크 저장 ─────────────────────────────────────────┐");
            BookmarkService.BookmarkUpdateResult result = bookmarkService.saveOrUpdate(BookmarkRequestDTO.from(event));
            log.info("│ ✅ 저장 완료");
            log.info("│ → 새로 읽은 문단 수: {}", result.newlyReadCount());
            log.info("│ → 챕터 총 문단 수: {}", result.chapterParagraphs());
            log.info("└─────────────────────────────────────────────────────────────┘");

            // [2] 북로그 저장
            log.info("┌─[STEP 2] 북로그 저장 ─────────────────────────────────────────┐");
            bookLogService.saveOrUpdate(new BookLogRequestDTO(
                    event.getLibraryId(),
                    event.getReadTime(),
                    result.newlyReadCount()));
            log.info("│ ✅ 저장 완료");
            log.info("│ → libraryId: {}, readTime: {}초, paragraphs: {}개",
                    event.getLibraryId(), event.getReadTime(), result.newlyReadCount());
            log.info("└─────────────────────────────────────────────────────────────┘");

            // [3] lastReadChapter 저장
            log.info("┌─[STEP 3] 마지막 읽은 챕터 저장 ───────────────────────────────┐");
            libraryService.saveLastChapter(event.getLibraryId(), event.getChapterId());
            log.info("│ ✅ 저장 완료");
            log.info("│ → libraryId: {} → lastReadChapterId: {}",
                    event.getLibraryId(), event.getChapterId());
            log.info("└─────────────────────────────────────────────────────────────┘");

            if (result.newlyReadCount() > 0) {
                // [5] 전체 진행률 동기화 및 마일스톤 체크
                log.info("┌─[STEP 5] 전체 진행률 동기화 및 마일스톤 체크 ──────────────────────────┐");
                Library library = libraryService.syncOverallProgress(event.getLibraryId(), result.newlyReadCount());
                log.info("│ ✅ 동기화 완료: {}", library != null ? library.getTotalProgress() + "%" : "null");

                if (library != null && library.getGainedWeight() > 0) {
                    float gainedWeight = library.getGainedWeight();
                    log.info("│ 🎉 마일스톤 달성! (누적 가중치: {}) → 취향 벡터 업데이트", gainedWeight);
                    userPreferenceService.updatePreferenceByProgress(event.getUserId(), event.getChapterId(),
                            gainedWeight);
                    log.info("│ ✅ 벡터 업데이트 완료");
                }
                log.info("└─────────────────────────────────────────────────────────────┘");
            }

            log.info("══════════════════════════════════════════════════════════════");
            log.info("🎉 독서 펄스 처리 완료! userId: {}, chapterId: {}", event.getUserId(), event.getChapterId());
            log.info("══════════════════════════════════════════════════════════════");

        } catch (Exception e) {

            log.error("스택 트레이스:", e);
        }
    }
}

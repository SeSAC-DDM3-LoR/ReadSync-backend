package com.ohgiraffers.backendapi.infra.event;

import com.ohgiraffers.backendapi.domain.bookmark.dto.BookmarkRequestDTO;
import com.ohgiraffers.backendapi.domain.bookmark.service.BookmarkService;
import com.ohgiraffers.backendapi.domain.booklog.dto.BookLogRequestDTO;
import com.ohgiraffers.backendapi.domain.booklog.service.BookLogService;
import com.ohgiraffers.backendapi.domain.library.service.LibraryService;
import com.ohgiraffers.backendapi.domain.user.service.UserPreferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReadingEventListener {

    private final BookmarkService bookmarkService;
    private final LibraryService libraryService;
    private final UserPreferenceService userPreferenceService;
    private final BookLogService bookLogService;

//    private final Map<String, Integer> pendingCounts = new ConcurrentHashMap<>();

    @Async
    @EventListener
    @Transactional // 하나의 트랜잭션으로 묶거나, 각 서비스 내부의 트랜잭션을 따름
    public void handleReadingEvent(ReadingEvent event) {

        log.info("독서 이벤트 처리 시작 - 유저: {}, 챕터: {}", event.getUserId(), event.getChapterId());

        try {
            BookmarkService.BookmarkUpdateResult result = bookmarkService.saveOrUpdate(BookmarkRequestDTO.from(event));
            bookLogService.saveOrUpdate(new BookLogRequestDTO(
                    event.getLibraryId(),
                    event.getReadTime(),
                    result.newlyReadCount() // 0이어도 상관없음 (기존 로그에 시간만 누적됨)
            ));

            if (result.newlyReadCount() > 0) {
                userPreferenceService.updatePreferenceByIncrement(
                        event.getUserId(),
                        event.getChapterId(),
                        result.newlyReadCount(),
                        result.chapterParagraphs()
                );
                libraryService.syncOverallProgress(
                        event.getLibraryId(),
                        result.newlyReadCount()
                );
//                String key = event.getUserId() + ":" + event.getChapterId();
//                int accumulated = pendingCounts.getOrDefault(key, 0) + result.newlyReadCount();
//                // 2. 임계치(예: 15문단) 체크
//                if (accumulated >= 15) {
//                    // 취향 벡터 업데이트 실행
//                    userPreferenceService.updatePreferenceByIncrement(
//                            event.getUserId(),
//                            event.getChapterId(),
//                            accumulated,
//                            result.totalParagraphs()
//                    );
//
//                    bookLogService.saveOrUpdate(new BookLogRequestDTO(
//                            event.getLibraryId(),
//                            event.getReadTime(),
//                            accumulated
//                    ));
//                    // 카운트 리셋
//                    pendingCounts.remove(key);
//                } else {
//                    // 아직 15개가 안 됐으면 메모리에 누적만 해둠
//                    pendingCounts.put(key, accumulated);
//                }
            }


        } catch (Exception e) {
            log.error("독서 이벤트 처리 중 오류 발생: {}", e.getMessage());
            // 필요한 경우 추가적인 예외 처리
        }
    }
}

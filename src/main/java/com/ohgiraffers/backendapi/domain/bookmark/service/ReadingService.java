package com.ohgiraffers.backendapi.domain.bookmark.service;

import com.ohgiraffers.backendapi.domain.bookmark.entity.Bookmark;
import com.ohgiraffers.backendapi.domain.bookmark.repository.BookmarkRepository;
import com.ohgiraffers.backendapi.domain.user.service.UserPreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class ReadingService {

    private final BookmarkRepository bookmarkRepository;
    private final UserPreferenceService userPreferenceService;
    // 유저별/도서별로 반영되지 않은 문단 수를 임시 저장 (서버가 1대일 때 예시)
    private final Map<String, Integer> pendingCounts = new ConcurrentHashMap<>();

    @Transactional
    public void syncReadingProgress(Long userId, Long bookmarkId, List<Integer> newIndices, int lastPos) {
        Bookmark bookmark = bookmarkRepository.findById(bookmarkId).orElseThrow();

        // 1. 북마크 비트마스크 업데이트 및 이번에 새로 읽은 개수 확인
        int newlyRead = bookmark.syncReadStatus(newIndices, lastPos);

        if (newlyRead > 0) {
            String key = userId + ":" + bookmark.getChapter().getChapterId();
            int accumulated = pendingCounts.getOrDefault(key, 0) + newlyRead;

            // 2. 임계치(예: 15문단) 체크
            if (accumulated >= 15) {
                // 취향 벡터 업데이트 실행
                userPreferenceService.updatePreferenceByIncrement(
                        userId,
                        bookmark.getChapter().getChapterId(),
                        accumulated,
                        bookmark.getChapter().getParagraphs()
                );
                // 카운트 리셋
                pendingCounts.remove(key);
            } else {
                // 아직 5개가 안 됐으면 메모리에 누적만 해둠
                pendingCounts.put(key, accumulated);
            }
        }
    }
}

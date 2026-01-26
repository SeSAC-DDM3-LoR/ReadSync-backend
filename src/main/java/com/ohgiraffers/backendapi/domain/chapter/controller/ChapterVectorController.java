package com.ohgiraffers.backendapi.domain.chapter.controller;

import com.ohgiraffers.backendapi.domain.chapter.service.ChapterVectorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "chapter-vector", description = "챕터 임베딩 및 AI 벡터 관리 API")
@RestController
@RequestMapping("/v1/chapters") // 다른 컨트롤러와 맞춰 v1 추가
@RequiredArgsConstructor
public class ChapterVectorController {

    private final ChapterVectorService chapterVectorService;

    @Operation(
            summary = "[관리자] 챕터 임베딩 실행",
            description = "특정 챕터의 S3/GD 파일을 읽어 AI 벡터를 생성합니다. 허깅페이스 서버 부팅을 고려하여 비동기로 처리됩니다."
    )
    @PreAuthorize("hasRole('ADMIN')") // AI 연산은 관리자만 호출 가능하도록 제한
    @PostMapping("/{chapterId}/embed")
    public ResponseEntity<String> createOrUpdateVector(
            @Parameter(description = "대상 챕터 ID") @PathVariable Long chapterId) {

        // 서비스 내부에서 @Async로 동작하므로, 사용자는 즉시 202 Accepted 응답을 받습니다.
        chapterVectorService.saveOrUpdateChapterVector(chapterId);

        return ResponseEntity.accepted()
                .body("챕터 ID " + chapterId + "의 벡터 생성 작업이 백그라운드에서 시작되었습니다. " +
                        "서버 상태에 따라 최대 3~5분이 소요될 수 있습니다.");
    }
}
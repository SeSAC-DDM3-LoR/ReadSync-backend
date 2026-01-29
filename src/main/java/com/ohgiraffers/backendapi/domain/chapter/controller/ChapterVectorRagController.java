package com.ohgiraffers.backendapi.domain.chapter.controller;

import com.ohgiraffers.backendapi.domain.chapter.service.ChapterVectorRagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chapter RAG Embedding", description = "챕터 RAG 임베딩 관리 API")
@RestController
@RequestMapping("/v1/chapters")
@RequiredArgsConstructor
public class ChapterVectorRagController {

    private final ChapterVectorRagService chapterVectorRagService;

    @PreAuthorize("hasRole('ADMIN')") // AI 연산은 관리자만 호출 가능하도록 제한
    @Operation(summary = "[관리자] RAG용 챕터 임베딩 생성 요청", description = "지정된 챕터의 S3 파일을 다운로드하여 문단 단위로 임베딩하고 저장합니다.")
    @PostMapping("/{chapterId}/rag-embedding")
    public ResponseEntity<String> createRagEmbedding(@PathVariable Long chapterId) {
        chapterVectorRagService.processRagEmbedding(chapterId);
        return ResponseEntity.ok("RAG 임베딩 작업이 백그라운드에서 시작되었습니다.");
    }

    @PreAuthorize("hasRole('ADMIN')") // AI 연산은 관리자만 호출 가능하도록 제한
    @Operation(summary = "[관리자] RAG 검색", description = "주어진 쿼리와 유사한 챕터 내용을 검색하여 문맥(Parent)을 반환합니다.")
    @org.springframework.web.bind.annotation.GetMapping("/{chapterId}/rag-search")
    public ResponseEntity<java.util.List<com.ohgiraffers.backendapi.domain.chapter.dto.rag.RagSearchResponseDTO>> searchRagChapter(
            @PathVariable Long chapterId,
            @org.springframework.web.bind.annotation.RequestParam String query) {
        return ResponseEntity.ok(chapterVectorRagService.searchRag(chapterId, query));
    }
}

package com.ohgiraffers.backendapi.domain.booklog.controller;

import com.ohgiraffers.backendapi.domain.booklog.dto.BookLogRequestDTO;
import com.ohgiraffers.backendapi.domain.booklog.dto.BookLogResponseDTO;
import com.ohgiraffers.backendapi.domain.booklog.service.BookLogService;
import com.ohgiraffers.backendapi.domain.user.dto.UserResponse;
import com.ohgiraffers.backendapi.global.common.annotation.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "BookLog", description = "독서 기록(로그) 관리 API")
@RestController
@RequestMapping("/v1/book-logs")
@RequiredArgsConstructor
public class BookLogController {

    private final BookLogService bookLogService;

    @Operation(summary = "특정 사용자의 전체 독서 기록 조회", description = "사용자 ID를 기반으로 해당 사용자의 모든 독서 로그 리스트를 가져옵니다.")
    @GetMapping("user/{userId}")
    public ResponseEntity<List<BookLogResponseDTO>> getLogByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(bookLogService.findAllByUser(userId));
    }

    @Operation(summary = "현재 로그인한 사용자의 독서 기록 조회", description = "인증 토큰 정보를 바탕으로 본인의 독서 로그 리스트를 조회합니다.")
    @GetMapping("me")
    public ResponseEntity<List<BookLogResponseDTO>> getMyBookLog(@CurrentUserId Long userId) {

        return ResponseEntity.ok(bookLogService.findAllByUser(userId));
    }

    /**
     * 독서 기록 저장 또는 누적 업데이트
     * POST /api/v1/book-logs
     */
    @Operation(summary = "독서 기록 저장 또는 누적 업데이트", description = "새로운 독서 기록을 저장하거나, 이미 해당 날짜에 기록이 있다면 내용을 누적하여 업데이트합니다.")
    @PostMapping
    public ResponseEntity<BookLogResponseDTO> saveOrUpdate(@RequestBody BookLogRequestDTO requestDTO) {
        BookLogResponseDTO response = bookLogService.saveOrUpdate(requestDTO);
        return ResponseEntity.ok(response);
    }

    /**
     * 독서 기록 상세 조회
     * GET /api/v1/book-logs/{bookLogId}
     */
    @Operation(summary = "독서 기록 상세 조회", description = "로그 ID를 기반으로 단일 독서 기록의 상세 정보를 조회합니다.")
    @GetMapping("book/{bookLogId}")
    public ResponseEntity<BookLogResponseDTO> getLog(@PathVariable Long bookLogId) {
        return ResponseEntity.ok(bookLogService.getLog(bookLogId));
    }

    /**
     * 독서 기록 삭제
     * DELETE /api/v1/book-logs/{bookLogId}
     */
    @Operation(summary = "독서 기록 영구 삭제",
            description = "독서 기록을 삭제합니다. **주의: 이 작업은 소프트 딜리트가 아닌 DB에서 데이터를 실제로 삭제하는 물리 삭제(Hard Delete)입니다.**")
    @DeleteMapping("/{bookLogId}")
    public ResponseEntity<Void> deleteLog(@PathVariable Long bookLogId) {
        bookLogService.deleteLog(bookLogId);
        return ResponseEntity.noContent().build();
    }
}
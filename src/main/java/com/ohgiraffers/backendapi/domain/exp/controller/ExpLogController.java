package com.ohgiraffers.backendapi.domain.exp.controller;

import com.ohgiraffers.backendapi.domain.exp.dto.ExpLogRequestDTO;
import com.ohgiraffers.backendapi.domain.exp.dto.ExpLogResponseDTO;
import com.ohgiraffers.backendapi.domain.exp.service.ExpLogService;
import com.ohgiraffers.backendapi.global.common.annotation.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "exp-log", description = "경험치 로그 및 보상 API")
@RestController
@RequestMapping("/v1/exp")
@RequiredArgsConstructor
public class ExpLogController {

    private final ExpLogService expLogService;

    @Operation(summary = "[관리자] 경험치 수동 지급", description = "관리자가 특정 유저에게 특정 활동에 대한 경험치를 직접 지급합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/give")
    public ResponseEntity<ExpLogResponseDTO> giveExperience(@RequestBody ExpLogRequestDTO requestDTO) {
        return ResponseEntity.ok(expLogService.giveExperience(requestDTO));
    }

    @Operation(summary = "[관리자] 타 유저 경험치 로그 조회 (페이징)", description = "관리자가 특정 유저의 모든 경험치 획득 내역을 조회합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<ExpLogResponseDTO>> getExpLogs(
            @PathVariable Long userId,
            @PageableDefault(size = 15, sort = "expLogId", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(expLogService.findAllByUser(userId, pageable));
    }

    @Operation(summary = "[사용자] 내 경험치 로그 조회 (페이징)", description = "현재 로그인한 사용자의 경험치 획득 내역을 조회합니다.")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping("/me")
    public ResponseEntity<Page<ExpLogResponseDTO>> getMyExpLogs(
            @CurrentUserId Long userId,
            @PageableDefault(size = 15, sort = "expLogId", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(expLogService.findAllByUser(userId, pageable));
    }
}
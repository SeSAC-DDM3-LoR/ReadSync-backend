package com.ohgiraffers.backendapi.domain.blacklist.controller;

import com.ohgiraffers.backendapi.domain.blacklist.dto.BlacklistRequest;
import com.ohgiraffers.backendapi.domain.blacklist.dto.BlacklistResponse;
import com.ohgiraffers.backendapi.domain.blacklist.entity.Blacklist;
import com.ohgiraffers.backendapi.domain.blacklist.service.BlacklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "Blacklist", description = "블랙리스트(제재) 관리 API")
@RestController
@RequestMapping("/v1/blacklists")
@RequiredArgsConstructor
public class BlacklistController {

    private final BlacklistService blacklistService;

    // [관리자] 블랙리스트 등록 (제재 시작)
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @Operation(summary = "[관리자] 유저 제재 등록", description = "특정 유저를 블랙리스트에 등록하여 제재합니다.")
    public ResponseEntity<String> addBlacklist(@RequestBody BlacklistRequest.Create request) {

        blacklistService.addBlacklist(
                request.getTargetUserId(),
                request.getType(),
                request.getReason(),
                request.getDurationDays()
        );
        return ResponseEntity.ok("해당 유저가 블랙리스트에 등록되었습니다.");
    }

    // [관리자] 현재 제재 중인 목록 조회
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    @Operation(summary = "[관리자] 제재 중인 목록 조회", description = "현재 활성화된 블랙리스트 내역을 조회합니다.")
    public ResponseEntity<List<BlacklistResponse>> getActiveBlacklists() {

        List<Blacklist> blacklists = blacklistService.getActiveBlacklists();
        List<BlacklistResponse> responses = blacklists.stream()
                .map(BlacklistResponse::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    // [관리자] 제재 해제 (isActive = false 처리)
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{blacklistId}")
    @Operation(summary = "[관리자] 제재 해제", description = "블랙리스트 내역을 해제 처리합니다. (기록은 유지됨)")
    public ResponseEntity<String> releaseBlacklist(@PathVariable Long blacklistId) {

        blacklistService.releaseBlacklist(blacklistId);
        return ResponseEntity.ok("제재가 해제되었습니다.");
    }
}
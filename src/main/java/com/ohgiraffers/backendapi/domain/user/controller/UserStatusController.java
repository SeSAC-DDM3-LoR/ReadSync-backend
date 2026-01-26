package com.ohgiraffers.backendapi.domain.user.controller;

import com.ohgiraffers.backendapi.domain.user.enums.UserActivityStatus;
import com.ohgiraffers.backendapi.domain.user.service.UserStatusService;
import com.ohgiraffers.backendapi.global.common.annotation.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "User Status", description = "사용자 접속 상태 관련 API")
@RestController
@RequestMapping("/v1/user-status")
@RequiredArgsConstructor
public class UserStatusController {

    private final UserStatusService userStatusService;

    @Operation(summary = "특정 유저 상태 조회", description = "특정 유저의 현재 접속 상태(ONLINE, OFFLINE, READING 등)를 조회합니다.")
    @GetMapping("/{userId}")
    public ResponseEntity<UserActivityStatus> getUserStatus(
            @Parameter(description = "조회할 유저 ID") @PathVariable Long userId) {
        UserActivityStatus status = userStatusService.getUserStatus(userId);
        return ResponseEntity.ok(status);
    }

    @Operation(summary = "내 상태 조회", description = "현재 로그인한 사용자의 접속 상태를 조회합니다.")
    @GetMapping("/my")
    public ResponseEntity<UserActivityStatus> getMyStatus(
            @Parameter(hidden = true) @CurrentUserId Long userId) {
        UserActivityStatus status = userStatusService.getUserStatus(userId);
        return ResponseEntity.ok(status);
    }
}

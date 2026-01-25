package com.ohgiraffers.backendapi.domain.user.controller;

import com.ohgiraffers.backendapi.domain.user.dto.UserRequest;
import com.ohgiraffers.backendapi.domain.user.dto.UserResponse;
import com.ohgiraffers.backendapi.domain.user.enums.UserStatus;
import com.ohgiraffers.backendapi.domain.user.service.UserService;
import com.ohgiraffers.backendapi.global.common.annotation.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "User", description = "유저 및 관리자 관련 API")
@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ==========================================
    // 👤 [일반 유저] - 본인 관련 기능
    // ==========================================

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "내 정보 조회", description = "현재 로그인한 유저의 상세 정보를 조회합니다.")
    public ResponseEntity<UserResponse.UserInfo> getMyProfile(@CurrentUserId Long userId) {
        return ResponseEntity.ok(userService.getMyProfile(userId));
    }

    @PatchMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "내 정보 수정", description = "닉네임, 프로필 사진 등을 수정합니다.")
    public ResponseEntity<UserResponse.UserInfo> updateProfile(
            @CurrentUserId Long userId,
            @RequestBody UserRequest.UpdateProfile request) {
        return ResponseEntity.ok(userService.updateProfile(userId, request));
    }

    @DeleteMapping("/me")
    @PreAuthorize("isAuthenticated()") // ★ 로그인한 누구나 접근 가능
    @Operation(summary = "회원 탈퇴", description = "계정을 영구 삭제하고 로그아웃 처리합니다.")
    public ResponseEntity<String> withdraw(@CurrentUserId Long userId) {
        userService.withdraw(userId);
        return ResponseEntity.ok("회원 탈퇴가 완료되었습니다.");
    }

    // ==========================================
    // 🔍 [공통 기능] - 타인 조회 및 검색
    // ==========================================

    @GetMapping("/{userId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "타인 프로필 조회", description = "다른 사용자의 공개 프로필 정보를 조회합니다. (민감정보 제외)")
    public ResponseEntity<UserResponse.OtherProfile> getOtherProfile(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getOtherProfile(userId));
    }

    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "유저 검색", description = "닉네임으로 유저를 검색합니다. (활성 유저만 검색됨)")
    public ResponseEntity<List<UserResponse.OtherProfile>> searchUsers(
            @RequestParam String keyword,
            Pageable pageable) {
        return ResponseEntity.ok(userService.searchUsers(keyword, pageable));
    }

    @GetMapping("/find")
    @PreAuthorize("isAuthenticated()") // ★ 로그인한 유저만 정확한 찾기 가능
    @Operation(summary = "유저 정확히 찾기 (닉네임#태그)", description = "닉네임과 태그(4자리)를 입력해 특정 유저를 정확하게 조회합니다.")
    public ResponseEntity<UserResponse.OtherProfile> findUserByTag(
            @RequestParam String nickname,
            @RequestParam String tag) {

        return ResponseEntity.ok(userService.findUserByTag(nickname, tag));
    }

    // ==========================================
    // 👑 [관리자 전용] - 회원 관리
    // ==========================================

    @GetMapping("/admin/list")
    @PreAuthorize("hasRole('ADMIN')") // ★ 관리자 권한 필수
    @Operation(summary = "[관리자] 전체 회원 조회", description = "가입된 모든 회원을 페이징하여 조회합니다.")
    public ResponseEntity<Page<UserResponse.AdminUserDetail>> getAllUsers(Pageable pageable) {

        return ResponseEntity.ok(userService.getAllUsers(pageable));
    }

    @GetMapping("/admin/{userId}/detail")
    @PreAuthorize("hasRole('ADMIN')") // ★ 관리자 권한 필수
    @Operation(summary = "[관리자] 회원 상세 정보 조회", description = "특정 회원의 모든 상세 정보를 조회합니다.")
    public ResponseEntity<UserResponse.UserDetail> getUserDetail(@PathVariable Long userId) {

        return ResponseEntity.ok(userService.getUserDetail(userId));
    }

    @PatchMapping("/admin/{userId}/status")
    @PreAuthorize("hasRole('ADMIN')") // ★ 관리자 권한 필수
    @Operation(summary = "[관리자] 회원 상태 변경", description = "특정 회원을 정지(BANNED)시키거나 상태를 변경합니다.")
    public ResponseEntity<String> changeStatus(
            @PathVariable Long userId,
            @RequestParam UserStatus status) {

        userService.changeStatus(userId, status);
        return ResponseEntity.ok("회원 상태가 " + status + "로 변경되었습니다.");
    }
}
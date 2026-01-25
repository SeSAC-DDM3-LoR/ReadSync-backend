package com.ohgiraffers.backendapi.domain.credit.controller;

import com.ohgiraffers.backendapi.domain.credit.dto.CreditResponse;
import com.ohgiraffers.backendapi.domain.credit.service.CreditService;
import com.ohgiraffers.backendapi.global.common.annotation.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/v1/credits")
@RequiredArgsConstructor
@Tag(name = "Credit", description = "크레딧(포인트) 관련 API")
public class CreditController {

    private final CreditService creditService;

    // [일반 유저 API]

    @GetMapping("/me")
    @Operation(summary = "내 크레딧 잔액 조회", description = "현재 사용 가능한 총 크레딧 합계를 반환합니다.")
    public ResponseEntity<Integer> getMyBalance(@CurrentUserId Long userId) {
        Integer balance = creditService.getMyTotalCredit(userId);
        return ResponseEntity.ok(balance);
    }

    // [테스트용] 크레딧 사용 (실제 서비스에선 주문 API 등 내부에서 Service 호출)
    @PostMapping("/consume")
    @Operation(summary = "[테스트] 크레딧 차감", description = "만료일이 가까운 크레딧부터 차감합니다.")
    public ResponseEntity<String> consumeCredit(
            @CurrentUserId Long userId,
            @RequestParam Integer amount
    ) {
        creditService.consumeCredit(userId, amount);
        return ResponseEntity.ok("차감 완료. 남은 잔액: " + creditService.getMyTotalCredit(userId));
    }

    //  [관리자 전용 API]

    @GetMapping("/admin/history")
    @PreAuthorize("hasRole('ADMIN')") // ★ 관리자만 접근 가능
    @Operation(summary = "[관리자] 전체 크레딧 내역 조회", description = "모든 유저의 지급/사용 내역을 조회합니다. (날짜 필터 가능)")
    public ResponseEntity<Page<CreditResponse.AdminCreditLog>> getAllHistory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(creditService.getAdminAllCredits(startDate, endDate, pageable));
    }

    @GetMapping("/admin/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')") // ★ 관리자만 접근 가능
    @Operation(summary = "[관리자] 특정 유저 크레딧 내역 조회", description = "특정 유저의 ID로 크레딧 이력을 조회합니다.")
    public ResponseEntity<Page<CreditResponse.AdminCreditLog>> getUserHistory(
            @PathVariable Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(creditService.getAdminUserCredits(userId, startDate, endDate, pageable));
    }

    @PostMapping("/admin/give")
    @PreAuthorize("hasRole('ADMIN')") // ★ 관리자만 접근 가능
    @Operation(summary = "[관리자] 크레딧 강제 지급", description = "관리자가 특정 유저에게 크레딧을 부여합니다.")
    public ResponseEntity<String> giveCredit(
            @RequestParam Long userId,
            @RequestParam Long creditTypeId,
            @RequestParam Integer amount
    ) {
        creditService.provideCredit(userId, creditTypeId, amount);
        return ResponseEntity.ok("지급 완료되었습니다.");
    }
}
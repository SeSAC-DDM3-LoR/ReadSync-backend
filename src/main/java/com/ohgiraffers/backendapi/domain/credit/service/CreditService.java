package com.ohgiraffers.backendapi.domain.credit.service;

import com.ohgiraffers.backendapi.domain.credit.dto.CreditResponse;
import com.ohgiraffers.backendapi.domain.credit.entity.Credit;
import com.ohgiraffers.backendapi.domain.credit.entity.CreditType;
import com.ohgiraffers.backendapi.domain.credit.enums.CreditStatus;
import com.ohgiraffers.backendapi.domain.credit.repository.CreditRepository;
import com.ohgiraffers.backendapi.domain.credit.repository.CreditTypeRepository;
import com.ohgiraffers.backendapi.domain.user.entity.User;
import com.ohgiraffers.backendapi.domain.user.repository.UserRepository;
import com.ohgiraffers.backendapi.global.error.CustomException;
import com.ohgiraffers.backendapi.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CreditService {

    private final CreditRepository creditRepository;
    private final CreditTypeRepository creditTypeRepository;
    private final UserRepository userRepository;

    // ==========================================
    // 👤 [사용자 기능]
    // ==========================================

    // 1. 내 잔액 조회
    @Transactional(readOnly = true)
    public Integer getMyTotalCredit(Long userId) {
        return creditRepository.calculateTotalAmount(userId);
    }

    // 2. 크레딧 지급 (시스템 내부 호출 or 어드민)
    @Transactional
    public void provideCredit(Long userId, Long creditTypeId, Integer amount) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        CreditType type = creditTypeRepository.findById(creditTypeId)
                .orElseThrow(() -> new CustomException(ErrorCode.INTERNAL_SERVER_ERROR)); // Type 없으면 에러

        // 만료일 계산: 현재시간 + 타입별 기본 유효기간
        LocalDateTime expiredAt = LocalDateTime.now().plusDays(type.getBaseExpiryDays());

        Credit credit = Credit.builder()
                .user(user)
                .creditType(type)
                .amount(amount)
                .expiredAt(expiredAt)
                .build();

        creditRepository.save(credit);
    }

    // 3. 크레딧 사용 (핵심: 만료 임박순 차감)
    @Transactional
    public void consumeCredit(Long userId, Integer amountToUse) {
        // 1) 총 잔액 확인
        Integer totalBalance = creditRepository.calculateTotalAmount(userId);
        if (totalBalance < amountToUse) {
            throw new CustomException(ErrorCode.INSUFFICIENT_CREDIT);
        }

        // 2) 사용 가능한 크레딧을 만료일 급한 순서대로 가져옴
        List<Credit> activeCredits = creditRepository.findAllByUserIdAndStatusOrderByExpiredAtAsc(
                userId, CreditStatus.ACTIVE
        );

        // 3) 순회하며 차감
        int remainingAmount = amountToUse;

        for (Credit credit : activeCredits) {
            if (remainingAmount <= 0) break;

            int available = credit.getAmount();

            if (available <= remainingAmount) {
                // 이 크레딧을 전액 소진해야 함
                credit.use(available); // 상태 USED로 변경됨
                remainingAmount -= available;
            } else {
                // 이 크레딧에서 일부만 차감하면 끝남
                credit.use(remainingAmount); // 상태 ACTIVE 유지, 잔액 감소
                remainingAmount = 0;
            }
        }
    }

    // ==========================================
    // 👑 [어드민 기능]
    // ==========================================

    // 4. [어드민] 전체 크레딧 로그 조회 (날짜 필터링 옵션)
    @Transactional(readOnly = true)
    public Page<CreditResponse.AdminCreditLog> getAdminAllCredits(LocalDate startDate, LocalDate endDate, Pageable pageable) {
        // 날짜 조건이 없으면 전체 조회
        if (startDate == null || endDate == null) {
            return creditRepository.findAllCredits(pageable)
                    .map(CreditResponse.AdminCreditLog::from);
        }

        // 날짜 조건이 있으면 범위 조회 (Start일의 00:00:00 ~ End일의 23:59:59)
        return creditRepository.findAllByDate(
                startDate.atStartOfDay(),
                endDate.atTime(23, 59, 59),
                pageable
        ).map(CreditResponse.AdminCreditLog::from);
    }

    // 5. [어드민] 특정 유저 크레딧 상세 조회
    @Transactional(readOnly = true)
    public Page<CreditResponse.AdminCreditLog> getAdminUserCredits(Long targetUserId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        // 유저 존재 확인
        if (!userRepository.existsById(targetUserId)) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }

        // 날짜가 없으면 기본적으로 전체 기간 (2000년 ~ 현재)
        LocalDateTime startDateTime = (startDate != null) ? startDate.atStartOfDay() : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime endDateTime = (endDate != null) ? endDate.atTime(23, 59, 59) : LocalDateTime.now();

        return creditRepository.findHistoryByUserIdAndDate(targetUserId, startDateTime, endDateTime, pageable)
                .map(CreditResponse.AdminCreditLog::from);
    }
}
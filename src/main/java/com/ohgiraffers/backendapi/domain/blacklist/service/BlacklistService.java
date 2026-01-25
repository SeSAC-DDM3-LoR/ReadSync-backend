package com.ohgiraffers.backendapi.domain.blacklist.service;

import com.ohgiraffers.backendapi.domain.blacklist.entity.Blacklist;
import com.ohgiraffers.backendapi.domain.blacklist.enums.BlacklistType;
import com.ohgiraffers.backendapi.domain.blacklist.repository.BlacklistRepository;
import com.ohgiraffers.backendapi.domain.user.entity.User;
import com.ohgiraffers.backendapi.domain.user.repository.UserRepository;
import com.ohgiraffers.backendapi.global.error.CustomException;
import com.ohgiraffers.backendapi.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlacklistService {

    private final BlacklistRepository blacklistRepository;
    private final UserRepository userRepository;

    /**
     * [공통/핵심] 사용자 차단 여부 검증
     * - 다른 서비스(Report, Comment 등)에서 이 메서드를 호출하여 권한을 체크합니다.
     * - 차단된 상태라면 즉시 예외(CustomException)를 던져 로직을 중단시킵니다.
     */
    public void validateUserBanStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 현재 시간 기준으로 활성화된(Active) + 만료되지 않은(EndDate > Now) 블랙리스트 내역 조회
        blacklistRepository.findActiveBlacklistByUser(user, LocalDateTime.now())
                .ifPresent(blacklist -> {
                    throw new CustomException(ErrorCode.USER_BANNED,
                            String.format("제재된 계정입니다. (사유: %s, 만료일: %s)",
                                    blacklist.getReason(), blacklist.getEndDate()));
                });
    }

    /**
     * [관리자] 블랙리스트 등록 (제재 시작)
     */
    @Transactional
    public void addBlacklist(Long targetUserId, BlacklistType type, String reason, int durationDays) {
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 이미 제재 중인지 확인 (중복 방지)
        blacklistRepository.findActiveBlacklistByUser(targetUser, LocalDateTime.now())
                .ifPresent(b -> {
                    throw new CustomException(ErrorCode.DUPLICATE_RESOURCE, "이미 제재 중인 사용자입니다.");
                });

        LocalDateTime endDate = LocalDateTime.now().plusDays(durationDays);

        Blacklist blacklist = Blacklist.builder()
                .user(targetUser)
                .type(type)
                .reason(reason)
                .startDate(LocalDateTime.now())
                .endDate(endDate)
                .build();

        blacklistRepository.save(blacklist);
    }

    /**
     * [관리자] 블랙리스트 해제 (조기 해제)
     * - 데이터를 삭제하지 않고 isActive를 false로 변경하여 기록 보존
     */
    @Transactional
    public void releaseBlacklist(Long blacklistId) {
        Blacklist blacklist = blacklistRepository.findById(blacklistId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "제재 내역을 찾을 수 없습니다."));

        blacklist.deactivate(); // isActive = false
    }

    /**
     * [관리자] 현재 제재 중인 목록 조회
     */
    public List<Blacklist> getActiveBlacklists() {
        return blacklistRepository.findAllActiveBlacklists(LocalDateTime.now());
    }
}
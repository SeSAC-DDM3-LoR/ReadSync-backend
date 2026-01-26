package com.ohgiraffers.backendapi.domain.blacklist.service;

import com.ohgiraffers.backendapi.domain.blacklist.entity.Blacklist;
import com.ohgiraffers.backendapi.domain.blacklist.enums.BlacklistType;
import com.ohgiraffers.backendapi.domain.blacklist.repository.BlacklistRepository;
import com.ohgiraffers.backendapi.domain.user.entity.User;
import com.ohgiraffers.backendapi.domain.user.repository.UserRepository;
import com.ohgiraffers.backendapi.global.error.CustomException;
import com.ohgiraffers.backendapi.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlacklistServiceTest {

    @InjectMocks
    private BlacklistService blacklistService;

    @Mock
    private BlacklistRepository blacklistRepository;

    @Mock
    private UserRepository userRepository;

    @Test
    @DisplayName("차단 여부 검증 성공: 차단되지 않은 유저는 예외가 발생하지 않는다.")
    void validateUserBanStatus_Success() {
        // given
        Long userId = 1L;
        User user = mock(User.class);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        // 활성화된 블랙리스트 내역 없음
        given(blacklistRepository.findActiveBlacklistByUser(eq(user), any(LocalDateTime.class)))
                .willReturn(Optional.empty());

        // when
        blacklistService.validateUserBanStatus(userId);

        // then
        // 예외가 발생하지 않으면 성공
    }

    @Test
    @DisplayName("차단 여부 검증 실패: 차단된 유저는 예외가 발생한다.")
    void validateUserBanStatus_Fail_Banned() {
        // given
        Long userId = 1L;
        User user = mock(User.class);
        Blacklist blacklist = mock(Blacklist.class);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        // 활성화된 블랙리스트 내역 존재
        given(blacklistRepository.findActiveBlacklistByUser(eq(user), any(LocalDateTime.class)))
                .willReturn(Optional.of(blacklist));

        // 에러 메시지 생성용 Stub
        given(blacklist.getReason()).willReturn("욕설");
        given(blacklist.getEndDate()).willReturn(LocalDateTime.now().plusDays(1));

        // when & then
        assertThatThrownBy(() -> blacklistService.validateUserBanStatus(userId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_BANNED);
    }

    @Test
    @DisplayName("블랙리스트 등록 성공: 새로운 제재를 등록한다.")
    void addBlacklist_Success() {
        // given
        Long targetUserId = 2L;
        User targetUser = mock(User.class);

        given(userRepository.findById(targetUserId)).willReturn(Optional.of(targetUser));
        // 기존 제재 내역 없음
        given(blacklistRepository.findActiveBlacklistByUser(eq(targetUser), any(LocalDateTime.class)))
                .willReturn(Optional.empty());

        // when
        blacklistService.addBlacklist(targetUserId, BlacklistType.SITE_BAN, "도배", 7);

        // then
        verify(blacklistRepository, times(1)).save(any(Blacklist.class));
    }

    @Test
    @DisplayName("블랙리스트 등록 실패: 이미 제재 중인 유저는 중복 등록할 수 없다.")
    void addBlacklist_Fail_Duplicate() {
        // given
        Long targetUserId = 2L;
        User targetUser = mock(User.class);
        Blacklist existingBlacklist = mock(Blacklist.class);

        given(userRepository.findById(targetUserId)).willReturn(Optional.of(targetUser));
        // 이미 제재 중
        given(blacklistRepository.findActiveBlacklistByUser(eq(targetUser), any(LocalDateTime.class)))
                .willReturn(Optional.of(existingBlacklist));

        // when & then
        assertThatThrownBy(() -> blacklistService.addBlacklist(targetUserId, BlacklistType.SITE_BAN, "도배", 7))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    @DisplayName("제재 해제: 블랙리스트를 비활성화(deactivate) 한다.")
    void releaseBlacklist_Success() {
        // given
        Long blacklistId = 100L;
        Blacklist blacklist = mock(Blacklist.class);

        given(blacklistRepository.findById(blacklistId)).willReturn(Optional.of(blacklist));

        // when
        blacklistService.releaseBlacklist(blacklistId);

        // then
        verify(blacklist, times(1)).deactivate();
    }
}
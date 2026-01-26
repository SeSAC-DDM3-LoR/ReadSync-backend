package com.ohgiraffers.backendapi.domain.user.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 사용자 온라인 상태 Enum
 * UserStatus와는 달리 실시간 접속 상태를 나타냄
 */
@Getter
@RequiredArgsConstructor
public enum UserActivityStatus {
    ONLINE("온라인"),
    OFFLINE("오프라인"),
    READING("독서중");

    private final String description;
}

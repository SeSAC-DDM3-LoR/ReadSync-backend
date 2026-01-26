package com.ohgiraffers.backendapi.domain.user.service;

import com.ohgiraffers.backendapi.domain.friendship.entity.Friendship;
import com.ohgiraffers.backendapi.domain.friendship.enums.FriendshipStatus;
import com.ohgiraffers.backendapi.domain.friendship.repository.FriendshipRepository;
import com.ohgiraffers.backendapi.domain.user.entity.User;
import com.ohgiraffers.backendapi.domain.user.enums.UserActivityStatus;
import com.ohgiraffers.backendapi.domain.user.enums.UserStatus;
import com.ohgiraffers.backendapi.domain.user.repository.UserRepository;
import com.ohgiraffers.backendapi.global.error.CustomException;
import com.ohgiraffers.backendapi.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 사용자 실시간 활동 상태 관리 서비스
 * Redis를 활용하여 사용자의 온라인/오프라인/독서중 상태를 캐싱하고
 * WebSocket을 통해 친구들에게 상태 변경을 실시간으로 알림
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserStatusService {

    private final RedisTemplate<String, String> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;

    private static final String STATUS_KEY_PREFIX = "user:status:";
    private static final long STATUS_EXPIRE_HOURS = 24;
    private static final String USER_SESSION_KEY = "user:sessions:";

    public void connectSession(Long userId, String sessionId) {
        String sessionKey = USER_SESSION_KEY + userId;

        // 1. 세션 ID를 Set에 추가
        redisTemplate.opsForSet().add(sessionKey, sessionId);
        redisTemplate.expire(sessionKey, Duration.ofHours(24)); // TTL 갱신

        // 2. 상태를 ONLINE으로 설정 (이미 온라인이어도 갱신)
        updateUserStatus(userId, UserActivityStatus.ONLINE);
    }

    // [수정] 퇴장 처리 (세션 제거 및 카운트 체크)
    public void disconnectSession(Long userId, String sessionId) {
        String sessionKey = USER_SESSION_KEY + userId;

        // 1. 해당 세션 ID만 제거
        redisTemplate.opsForSet().remove(sessionKey, sessionId);

        // 2. 남은 세션 개수 확인
        Long size = redisTemplate.opsForSet().size(sessionKey);

        // 3. 세션이 하나도 남지 않았을 때만 OFFLINE 처리
        if (size == null || size == 0) {
            updateUserStatus(userId, UserActivityStatus.OFFLINE);
        }
    }

    /**
     * 사용자 상태 업데이트 및 친구들에게 알림
     */
    public void updateUserStatus(Long userId, UserActivityStatus status) {
        // Redis에 상태 저장 (24시간 만료)
        String key = STATUS_KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(key, status.name(), STATUS_EXPIRE_HOURS, TimeUnit.HOURS);

        log.info("User {} status updated to {}", userId, status);

        // 친구들에게 상태 변경 알림
        notifyFriendsStatusChange(userId, status);
    }

    /**
     * 사용자 상태 조회
     */
    public UserActivityStatus getUserStatus(Long userId) {
        String key = STATUS_KEY_PREFIX + userId;
        String status = redisTemplate.opsForValue().get(key);

        if (status == null) {
            return UserActivityStatus.OFFLINE;
        }

        try {
            return UserActivityStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid user status in Redis for user {}: {}", userId, status);
            return UserActivityStatus.OFFLINE;
        }
    }

    /**
     * 친구들에게 상태 변경 알림
     */
    private void notifyFriendsStatusChange(Long userId, UserActivityStatus status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 해당 유저의 친구 목록 조회
        List<Friendship> friendships = friendshipRepository.findMyFriendships(userId, FriendshipStatus.ACCEPTED);

        friendships.forEach(friendship -> {
            // 친구 ID 찾기 (내가 requester면 addressee가 친구, 아니면 requester가 친구)
            Long friendId = friendship.getRequester().getId().equals(userId)
                    ? friendship.getAddressee().getId()
                    : friendship.getRequester().getId();

            // WebSocket을 통해 친구에게 상태 변경 알림
            StatusUpdateMessage message = new StatusUpdateMessage(
                    userId,
                    user.getUserInformation() != null ? user.getUserInformation().getNickname() : user.getLoginId(),
                    status);

            messagingTemplate.convertAndSend("/topic/status/" + friendId, message);
            log.debug("Status update sent to friend {}: user {} is now {}", friendId, userId, status);
        });
    }

    /**
     * WebSocket 메시지 DTO
     */
    @lombok.Value
    public static class StatusUpdateMessage {
        Long userId;
        String displayName;
        UserActivityStatus status;
    }
}

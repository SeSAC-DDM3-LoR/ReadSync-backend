package com.ohgiraffers.backendapi.domain.blacklist.entity;

import com.ohgiraffers.backendapi.domain.blacklist.enums.BlacklistType;
import com.ohgiraffers.backendapi.domain.user.entity.User;
import com.ohgiraffers.backendapi.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "blacklists")
public class Blacklist extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "blacklist_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BlacklistType type; // 예: BAN, MUTE 등

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    // [New] 제재가 해제되어도 기록은 남기되, 활성 여부로 현재 상태 판단
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Builder
    public Blacklist(User user, BlacklistType type, String reason, LocalDateTime startDate, LocalDateTime endDate) {
        this.user = user;
        this.type = type;
        this.reason = reason;
        this.startDate = startDate != null ? startDate : LocalDateTime.now();
        this.endDate = endDate;
        this.isActive = true;
    }

    // 제재 조기 해제 (기록은 남음)
    public void deactivate() {
        this.isActive = false;
    }

    // 기간 만료 여부 확인
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.endDate);
    }
}
package com.ohgiraffers.backendapi.domain.credit.entity;

import com.ohgiraffers.backendapi.domain.credit.enums.CreditStatus;
import com.ohgiraffers.backendapi.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "credits")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Credit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "credits_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credit_type_id", nullable = false)
    private CreditType creditType;

    @Column(nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CreditStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public Credit(User user, CreditType creditType, Integer amount, LocalDateTime expiredAt) {
        this.user = user;
        this.creditType = creditType;
        this.amount = amount;
        this.expiredAt = expiredAt;
        this.status = CreditStatus.ACTIVE;
        this.createdAt = LocalDateTime.now();
    }

    // 차감
    public void use(Integer usageAmount) {
        if (this.amount < usageAmount) {
            throw new IllegalArgumentException("차감할 잔액이 부족합니다.");
        }
        this.amount -= usageAmount;

        if (this.amount == 0) {
            this.status = CreditStatus.USED;
            this.usedAt = LocalDateTime.now();
        }
    }

    //  만료
    public void expire() {
        if (this.status == CreditStatus.ACTIVE) {
            this.status = CreditStatus.EXPIRED;
        }
    }
}
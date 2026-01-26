package com.ohgiraffers.backendapi.domain.subscription.entity;

import com.ohgiraffers.backendapi.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Table(name = "subscription_plans")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SubscriptionPlan extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plan_id")
    private Long planId;

    @Column(name = "plan_name", nullable = false, length = 50)
    private String planName;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Column(name = "description")
    private String description;

    @Column(name = "give_credit")
    private Integer giveCredit;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

}

package com.ohgiraffers.backendapi.domain.credit.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "credit_type")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "credit_type_id")
    private Long id;

    @Column(name = "credit_name", nullable = false, length = 20)
    private String name;

    @Column(name = "base_expiry_days", nullable = false)
    private Integer baseExpiryDays;

    // 관리자가 타입을 추가할 때 사용할 생성자
    public CreditType(String name, Integer baseExpiryDays) {
        this.name = name;
        this.baseExpiryDays = baseExpiryDays;
    }
}
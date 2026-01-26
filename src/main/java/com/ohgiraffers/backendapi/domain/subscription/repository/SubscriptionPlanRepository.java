package com.ohgiraffers.backendapi.domain.subscription.repository;

import com.ohgiraffers.backendapi.domain.subscription.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {
    Optional<SubscriptionPlan> findByPlanName(String planName);
}

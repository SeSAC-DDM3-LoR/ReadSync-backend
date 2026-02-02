package com.ohgiraffers.backendapi.domain.order.repository;

import com.ohgiraffers.backendapi.domain.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 주문 엔티티를 관리하는 JPA 리포지토리입니다.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    org.springframework.data.domain.Page<Order> findByUser(com.ohgiraffers.backendapi.domain.user.entity.User user,
            org.springframework.data.domain.Pageable pageable);

    java.util.Optional<Order> findByOrderUid(String orderUid);
}

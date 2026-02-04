package com.ohgiraffers.backendapi.domain.order.dto;

import com.ohgiraffers.backendapi.domain.order.entity.Order;
import com.ohgiraffers.backendapi.domain.order.enums.OrderStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class OrderResponse {
    private Long orderId;
    private String orderUid;
    private String orderName;
    private BigDecimal totalAmount;
    private OrderStatus status;
    private LocalDateTime createdAt;
    private String receiptUrl; // 결제 영수증 URL (PaymentHistory에서 가져와야 함, 일단 null 가능)

    public static OrderResponse from(Order order) {
        return OrderResponse.builder()
                .orderId(order.getOrderId())
                .orderUid(order.getOrderUid())
                .orderName(order.getOrderName())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .build();
    }

    // 영수증 URL을 포함하는 팩토리 메서드
    public static OrderResponse from(Order order, String receiptUrl) {
        return OrderResponse.builder()
                .orderId(order.getOrderId())
                .orderUid(order.getOrderUid())
                .orderName(order.getOrderName())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .receiptUrl(receiptUrl)
                .build();
    }
}

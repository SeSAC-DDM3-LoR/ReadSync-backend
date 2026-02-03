package com.ohgiraffers.backendapi.domain.order.controller;

import com.ohgiraffers.backendapi.domain.order.dto.OrderResponse;
import com.ohgiraffers.backendapi.domain.order.service.OrderService;
import com.ohgiraffers.backendapi.global.common.annotation.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Order (주문)", description = "주문 및 결제 결제 내역 API")
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "내 결제(주문) 내역 조회", description = "단건 결제(주문) 내역을 조회합니다. (정기 결제 제외)")
    @GetMapping("/me")
    public ResponseEntity<Page<OrderResponse>> getMyOrders(
            @CurrentUserId Long userId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<OrderResponse> response = orderService.getMyOrders(userId, pageable);
        return ResponseEntity.ok(response);
    }
}

package com.ohgiraffers.backendapi.domain.payment.service;

import com.ohgiraffers.backendapi.domain.payment.dto.BillingKeyRequest;
import com.ohgiraffers.backendapi.domain.payment.entity.PaymentMethod;
import com.ohgiraffers.backendapi.domain.payment.enums.PgProvider;
import com.ohgiraffers.backendapi.domain.payment.repository.PaymentMethodRepository;
import com.ohgiraffers.backendapi.domain.user.entity.User;
import com.ohgiraffers.backendapi.domain.user.repository.UserRepository;
import com.ohgiraffers.backendapi.global.error.CustomException;
import com.ohgiraffers.backendapi.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 결제 관련 비즈니스 로직을 처리하는 서비스 클래스입니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final TossPaymentService tossPaymentService;
    private final PaymentMethodRepository paymentMethodRepository;
    private final UserRepository userRepository;
    private final com.ohgiraffers.backendapi.domain.order.repository.OrderRepository orderRepository;
    private final com.ohgiraffers.backendapi.domain.payment.repository.PaymentHistoryRepository paymentHistoryRepository;

    /**
     * 빌링키를 발급받아 결제 수단으로 등록합니다.
     * 
     * @param userId  사용자 ID
     * @param request 빌링키 발급 요청 정보
     */
    @Transactional
    public void registerPaymentMethod(Long userId, BillingKeyRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 토스 API를 통해 빌링키 발급
        Map<String, Object> result = tossPaymentService.issueBillingKey(request);

        String billingKey = (String) result.get("billingKey");
        String cardCompany = "";
        String cardLast4 = "";

        if (result.containsKey("card")) {
            Map<String, Object> cardInfo = (Map<String, Object>) result.get("card");
            cardCompany = (String) cardInfo.get("issuerCode"); // 또는 company 등으로 변경 가능
            cardLast4 = (String) cardInfo.get("number");
            if (cardLast4 != null && cardLast4.length() >= 4) {
                cardLast4 = cardLast4.substring(cardLast4.length() - 4);
            }
        }

        // 기존 기본 결제 수단 해제 (필요시)
        paymentMethodRepository.findByUserAndIsDefaultTrueAndDeletedAtIsNull(user)
                .ifPresent(m -> {
                    m.updateDefaultStatus(false);
                    paymentMethodRepository.save(m);
                });

        PaymentMethod paymentMethod = PaymentMethod.builder()
                .user(user)
                .billingKey(billingKey)
                .pgProvider(PgProvider.TOSS)
                .cardCompany(cardCompany)
                .cardLast4(cardLast4)
                .customerKey(request.getCustomerKey())
                .isDefault(true)
                .build();

        paymentMethodRepository.save(paymentMethod);
    }

    /**
     * 토스 페이먼츠 웹훅을 처리합니다.
     * 
     * @param request 웹훅 요청 정보
     */
    @Transactional
    public void handleWebhook(com.ohgiraffers.backendapi.domain.webhook.dto.TossWebhookRequest request) {
        String eventType = request.getEventType();
        String status = request.getData().getStatus();
        String orderUid = request.getData().getOrderId();

        if (orderUid == null) {
            // 주문 ID가 없는 경우 처리 불가 (로깅만 하고 종료)
            return;
        }

        com.ohgiraffers.backendapi.domain.order.entity.Order order = orderRepository.findByOrderUid(orderUid)
                .orElseThrow(() -> new CustomException(ErrorCode.ORDER_NOT_FOUND));

        if ("PAYMENT_STATUS_CHANGED".equals(eventType) || "DEPOSIT_CALLBACK".equals(eventType)) {
            if ("DONE".equals(status)) {
                // 결제 완료 (가상계좌 입금 완료 등)
                // 주문 상태 변경
                order.updateStatus(com.ohgiraffers.backendapi.domain.order.enums.OrderStatus.COMPLETED);

                // 결제 내역 업데이트
                paymentHistoryRepository.findTopByOrderOrderByCreatedAtDesc(order)
                        .ifPresent(history -> history
                                .updateStatus(com.ohgiraffers.backendapi.domain.payment.enums.PaymentStatus.DONE));

            } else if ("CANCELED".equals(status)) {
                // 결제 취소
                order.updateStatus(com.ohgiraffers.backendapi.domain.order.enums.OrderStatus.CANCELED);

                // 결제 내역 업데이트
                paymentHistoryRepository.findTopByOrderOrderByCreatedAtDesc(order)
                        .ifPresent(history -> history
                                .updateStatus(com.ohgiraffers.backendapi.domain.payment.enums.PaymentStatus.CANCELED));
            }
        }
    }

    /**
     * 사용자의 빌링키(결제 수단) 등록 여부를 확인합니다.
     *
     * @param userId 사용자 ID
     * @return 빌링키가 등록되어 있으면 true, 아니면 false
     */
    public boolean hasBillingKey(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }
        return !paymentMethodRepository.findByUserAndDeletedAtIsNull(user).isEmpty();
    }
}

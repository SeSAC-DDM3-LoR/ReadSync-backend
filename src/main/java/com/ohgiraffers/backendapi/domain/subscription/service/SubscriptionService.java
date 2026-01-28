package com.ohgiraffers.backendapi.domain.subscription.service;

import com.ohgiraffers.backendapi.domain.order.entity.Order;
import com.ohgiraffers.backendapi.domain.order.repository.OrderRepository;
import com.ohgiraffers.backendapi.domain.payment.entity.PaymentHistory;
import com.ohgiraffers.backendapi.domain.payment.entity.PaymentMethod;
import com.ohgiraffers.backendapi.domain.payment.enums.PaymentStatus;
import com.ohgiraffers.backendapi.domain.payment.enums.PgProvider;
import com.ohgiraffers.backendapi.domain.payment.enums.TransactionType;
import com.ohgiraffers.backendapi.domain.payment.repository.PaymentHistoryRepository;
import com.ohgiraffers.backendapi.domain.payment.repository.PaymentMethodRepository;
import com.ohgiraffers.backendapi.domain.payment.service.TossPaymentService;
import com.ohgiraffers.backendapi.domain.subscription.dto.SubscriptionResponse;
import com.ohgiraffers.backendapi.domain.subscription.entity.Subscription;
import com.ohgiraffers.backendapi.domain.subscription.entity.SubscriptionPlan;
import com.ohgiraffers.backendapi.domain.subscription.enums.SubscriptionStatus;
import com.ohgiraffers.backendapi.domain.subscription.repository.SubscriptionPlanRepository;
import com.ohgiraffers.backendapi.domain.subscription.repository.SubscriptionRepository;
import com.ohgiraffers.backendapi.domain.user.entity.User;
import com.ohgiraffers.backendapi.domain.user.repository.UserRepository;
import com.ohgiraffers.backendapi.global.error.CustomException;
import com.ohgiraffers.backendapi.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 구독 관리 서비스 클래스입니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionService {

        private final SubscriptionRepository subscriptionRepository;
        private final com.ohgiraffers.backendapi.domain.credit.service.CreditService creditService;
        private final SubscriptionPlanRepository subscriptionPlanRepository;
        private final PaymentMethodRepository paymentMethodRepository;
        private final PaymentHistoryRepository paymentHistoryRepository;
        private final TossPaymentService tossPaymentService;
        private final UserRepository userRepository;
        private final OrderRepository orderRepository;

        /**
         * 정기 구독 신청 (첫 결제 포함)
         */
        @Transactional
        public SubscriptionResponse subscribe(Long userId, Long planId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

                SubscriptionPlan plan = subscriptionPlanRepository.findById(planId)
                                .orElseThrow(() -> new CustomException(ErrorCode.INTERNAL_SERVER_ERROR,
                                                "존재하지 않는 구독 플랜입니다."));

                // 이미 구독 중인지 확인
                subscriptionRepository.findByUserAndSubscriptionStatus(user, SubscriptionStatus.ACTIVE)
                                .ifPresent(s -> {
                                        throw new CustomException(ErrorCode.ALREADY_SUBSCRIBED);
                                });

                // 등록된 결제 수단 확인 (중복 방지 처리)
                List<PaymentMethod> paymentMethods = paymentMethodRepository
                                .findByUserAndIsDefaultTrueAndDeletedAtIsNullOrderByCreatedAtDesc(user);

                if (paymentMethods.isEmpty()) {
                        throw new CustomException(ErrorCode.PAYMENT_METHOD_NOT_FOUND);
                }

                // 가장 최근 등록된 기본 결제 수단 사용
                PaymentMethod paymentMethod = paymentMethods.get(0);

                // 중복된 기본 결제 수단이 있으면 나머지는 기본 해제 (데이터 정리)
                if (paymentMethods.size() > 1) {
                        for (int i = 1; i < paymentMethods.size(); i++) {
                                PaymentMethod duplicate = paymentMethods.get(i);
                                duplicate.updateDefaultStatus(false);
                                paymentMethodRepository.save(duplicate);
                        }
                }

                // 결제 수단 유효성 검증
                if (paymentMethod.getCustomerKey() == null) {
                        throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "결제 수단 정보가 만료되었습니다. 카드를 다시 등록해주세요.");
                }

                // 첫 결제 수행
                String orderId = UUID.randomUUID().toString();
                Map<String, Object> paymentResult = tossPaymentService.executeBillingPayment(
                                paymentMethod.getBillingKey(),
                                paymentMethod.getCustomerKey(),
                                plan.getPrice(),
                                orderId,
                                plan.getPlanName() + " 구독");

                // 결제 성공 시 데이터 저장
                Subscription subscription = Subscription.builder()
                                .user(user)
                                .plan(plan)
                                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                                .startedAt(LocalDateTime.now())
                                .nextBillingDate(LocalDateTime.now().plusMonths(1))
                                .build();

                Subscription savedSubscription = subscriptionRepository.save(subscription);

                // 정기 결제용 주문 생성 (DB FK 제약조건 준수)
                Order subscriptionOrder = Order.builder()
                                .user(user)
                                .paymentMethod(paymentMethod)
                                .orderUid(orderId)
                                .orderName(plan.getPlanName() + " 구독 (첫 결제)")
                                .totalAmount(plan.getPrice())
                                .status(com.ohgiraffers.backendapi.domain.order.enums.OrderStatus.COMPLETED)
                                .subscription(savedSubscription)
                                .build();

                Order savedOrder = orderRepository.save(subscriptionOrder);

                // 결제 내역 저장
                PaymentHistory history = PaymentHistory.builder()
                                .order(savedOrder)
                                .pgPaymentKey((String) paymentResult.get("paymentKey"))
                                .amount(plan.getPrice())
                                .paymentStatus(PaymentStatus.DONE)
                                .transType(TransactionType.PAY)
                                .pgProvider(PgProvider.TOSS)
                                .build();
                paymentHistoryRepository.save(history);

                // 크레딧 지급
                if (plan.getGiveCredit() != null && plan.getGiveCredit() > 0) {
                        try {
                                creditService.provideCredit(user.getId(), 4L, plan.getGiveCredit());
                                log.info("구독 크레딧 지급 완료 - User: {}, Amount: {}", user.getId(), plan.getGiveCredit());
                        } catch (Exception e) {
                                log.error("구독 크레딧 지급 실패: {}", e.getMessage());
                                // 크레딧 지급 실패가 구독 실패로 이어지지는 않도록 처리
                        }
                }

                return convertToResponse(savedSubscription);
        }

        /**
         * 구독 해지 처리
         */
        @Transactional
        public void cancelSubscription(Long userId, Long subId) {
                Subscription subscription = subscriptionRepository.findById(subId)
                                .orElseThrow(() -> new CustomException(ErrorCode.INTERNAL_SERVER_ERROR,
                                                "구독 정보를 찾을 수 없습니다."));

                if (!subscription.getUser().getId().equals(userId)) {
                        throw new CustomException(ErrorCode.ACCESS_DENIED);
                }

                Subscription updatedSubscription = Subscription.builder()
                                .subId(subscription.getSubId())
                                .user(subscription.getUser())
                                .plan(subscription.getPlan())
                                .subscriptionStatus(SubscriptionStatus.CANCELED)
                                .startedAt(subscription.getStartedAt())
                                .nextBillingDate(subscription.getNextBillingDate())
                                .endedAt(LocalDateTime.now())
                                .build();

                subscriptionRepository.save(updatedSubscription);
        }

        /**
         * 정기 결제 스케줄러 (매일 자정에 실행)
         */
        @Scheduled(cron = "0 0 0 * * *")
        @Transactional
        public void processScheduledRenewals() {
                log.info("정기 결제 프로세스 시작: {}", LocalDateTime.now());

                List<Subscription> dueSubscriptions = subscriptionRepository
                                .findBySubscriptionStatusAndNextBillingDateBefore(
                                                SubscriptionStatus.ACTIVE, LocalDateTime.now());

                log.info("처리할 구독 수: {}", dueSubscriptions.size());

                for (Subscription sub : dueSubscriptions) {
                        try {
                                renewSubscription(sub);
                                log.info("구독 갱신 성공 (ID: {}, User: {})", sub.getSubId(), sub.getUser().getId());
                        } catch (Exception e) {
                                log.error("구독 갱신 실패 (ID: {}): {}", sub.getSubId(), e.getMessage());
                                handleRenewalFailure(sub, e.getMessage());
                        }
                }

                // 결제 실패 후 7일이 지난 구독은 만료 처리
                processExpiredSubscriptions();
        }

        /**
         * 개별 구독 갱신 처리
         */
        @Transactional
        public void renewSubscription(Subscription sub) {
                PaymentMethod paymentMethod = paymentMethodRepository
                                .findByUserAndIsDefaultTrueAndDeletedAtIsNull(sub.getUser())
                                .orElseThrow(() -> new RuntimeException("결제 수단 없음"));

                String orderId = UUID.randomUUID().toString();
                Map<String, Object> paymentResult = tossPaymentService.executeBillingPayment(
                                paymentMethod.getBillingKey(),
                                paymentMethod.getCustomerKey(),
                                sub.getPlan().getPrice(),
                                orderId,
                                sub.getPlan().getPlanName() + " 정기 갱신");

                // 다음 결제일 업데이트
                Subscription updatedSub = Subscription.builder()
                                .subId(sub.getSubId())
                                .user(sub.getUser())
                                .plan(sub.getPlan())
                                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                                .startedAt(sub.getStartedAt())
                                .nextBillingDate(sub.getNextBillingDate().plusMonths(1))
                                .build();
                subscriptionRepository.save(updatedSub);

                // 갱신용 주문 생성
                Order renewalOrder = Order.builder()
                                .user(sub.getUser())
                                .paymentMethod(paymentMethod)
                                .orderUid(orderId)
                                .orderName(sub.getPlan().getPlanName() + " 정기 갱신")
                                .totalAmount(sub.getPlan().getPrice())
                                .status(com.ohgiraffers.backendapi.domain.order.enums.OrderStatus.COMPLETED)
                                .subscription(updatedSub)
                                .build();
                Order savedOrder = orderRepository.save(renewalOrder);

                // 결제 내역 저장
                PaymentHistory history = PaymentHistory.builder()
                                .order(savedOrder)
                                .pgPaymentKey((String) paymentResult.get("paymentKey"))
                                .amount(sub.getPlan().getPrice())
                                .paymentStatus(PaymentStatus.DONE)
                                .transType(TransactionType.PAY)
                                .pgProvider(PgProvider.TOSS)
                                .build();
                paymentHistoryRepository.save(history);

                // 크레딧 지급 (갱신 시에도 지급)
                if (sub.getPlan().getGiveCredit() != null && sub.getPlan().getGiveCredit() > 0) {
                        try {
                                creditService.provideCredit(sub.getUser().getId(), 4L, sub.getPlan().getGiveCredit());
                                log.info("구독 갱신 크레딧 지급 완료 - User: {}, Amount: {}", sub.getUser().getId(),
                                                sub.getPlan().getGiveCredit());
                        } catch (Exception e) {
                                log.error("구독 갱신 크레딧 지급 실패: {}", e.getMessage());
                        }
                }
        }

        /**
         * 결제 실패 처리
         */
        @Transactional
        public void handleRenewalFailure(Subscription sub, String reason) {
                // 구독 상태를 PAYMENT_FAILED로 변경
                Subscription failedSub = Subscription.builder()
                                .subId(sub.getSubId())
                                .user(sub.getUser())
                                .plan(sub.getPlan())
                                .subscriptionStatus(SubscriptionStatus.PAYMENT_FAILED)
                                .startedAt(sub.getStartedAt())
                                .nextBillingDate(sub.getNextBillingDate())
                                .build();
                subscriptionRepository.save(failedSub);

                log.warn("구독 결제 실패 - ID: {}, 사유: {}", sub.getSubId(), reason);
                // TODO: 사용자에게 알림 발송 (이메일, 푸시 등)
        }

        /**
         * 결제 실패 후 7일이 지난 구독 만료 처리
         */
        @Transactional
        public void processExpiredSubscriptions() {
                List<Subscription> failedSubscriptions = subscriptionRepository
                                .findBySubscriptionStatusAndNextBillingDateBefore(
                                                SubscriptionStatus.PAYMENT_FAILED, LocalDateTime.now().minusDays(7));

                for (Subscription sub : failedSubscriptions) {
                        Subscription expiredSub = Subscription.builder()
                                        .subId(sub.getSubId())
                                        .user(sub.getUser())
                                        .plan(sub.getPlan())
                                        .subscriptionStatus(SubscriptionStatus.EXPIRED)
                                        .startedAt(sub.getStartedAt())
                                        .nextBillingDate(sub.getNextBillingDate())
                                        .endedAt(LocalDateTime.now())
                                        .build();
                        subscriptionRepository.save(expiredSub);
                        log.info("구독 만료 처리 - ID: {}, User: {}", sub.getSubId(), sub.getUser().getId());
                }
        }

        private SubscriptionResponse convertToResponse(Subscription sub) {
                return SubscriptionResponse.from(sub);
        }

        public SubscriptionResponse getMySubscription(Long userId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

                return subscriptionRepository.findByUserAndSubscriptionStatus(user, SubscriptionStatus.ACTIVE)
                                .map(this::convertToResponse)
                                .orElse(null);
        }
}

package com.event.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentProcessor {

    private static final BigDecimal DECLINE_ABOVE = new BigDecimal("1000000");

    private final PaymentRepository paymentRepository;

    @Value("${payment.bank.latency-ms:50}")
    private long bankLatencyMs;

    @KafkaListener(topics = PaymentService.TOPIC)
    public void consumePaymentEvent(PaymentEvent event) {
        log.info("Received PaymentEvent for payment {}", event.paymentId());

        Payment payment = paymentRepository.findById(event.paymentId())
                .orElseThrow(() -> new IllegalStateException("Payment not found: " + event.paymentId()));

        // consumer-side idempotency: covers COMPLETED and FAILED, not just COMPLETED
        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info("Payment {} already {}; skipping", payment.getId(), payment.getStatus());
            return;
        }

        boolean approved;
        try {
            approved = callBank(event);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();          // don't swallow the interrupt
            throw new IllegalStateException("Interrupted while calling bank", e);
        }

        // conditional UPDATE ... WHERE status='PENDING' -> safe even if two consumers race
        if (approved) {
            paymentRepository.markCompleted(payment.getId(), LocalDateTime.now());
            log.info("Payment {} COMPLETED", payment.getId());
        } else {
            paymentRepository.markFailed(payment.getId(), "Declined: amount exceeds limit", LocalDateTime.now());
            log.warn("Payment {} declined", payment.getId());
        }
    }

    // Simulated bank. In real life pass the idempotency key downstream so the bank can de-duplicate too.
    private boolean callBank(PaymentEvent event) throws InterruptedException {
        Thread.sleep(bankLatencyMs);
        return event.amount().compareTo(DECLINE_ABOVE) <= 0;
    }
}
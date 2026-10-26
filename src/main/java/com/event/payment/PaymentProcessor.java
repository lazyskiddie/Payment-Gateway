package com.event.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentProcessor {

    private final PaymentRepository paymentRepository;

    @Transactional
    @KafkaListener(topics = "payment-events", groupId = "payment-processing-group")
    public void consumePaymentEvent(PaymentEvent event) {
        log.info("Received PaymentEvent for Idempotency Key: {}", event.idempotencyKey());

        // Fetch the database record
        Payment payment = paymentRepository.findByIdempotencyKey(event.idempotencyKey())
                .orElseThrow(() -> new IllegalStateException("Payment record not found for key: " + event.idempotencyKey()));

        // Double-check Idempotency (Consumer-side protection)
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            log.warn("Payment {} already processed. Skipping.", event.idempotencyKey());
            return;
        }

        try {
            // Simulate processing time (e.g., calling external bank APIs, fraud checks)
            log.info("Processing transaction of ${} from {} to {}...",
                    event.amount(), event.sourceAccount(), event.destinationAccount());
            Thread.sleep(2000);

            // Mark as completed
            payment.setStatus(PaymentStatus.COMPLETED);
            paymentRepository.save(payment);
            log.info("Successfully processed payment. Status updated to COMPLETED.");

        } catch (Exception e) {
            log.error("Payment processing failed", e);
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
        }
    }
}

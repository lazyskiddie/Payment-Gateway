package com.event.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;
    private static final String TOPIC = "payment-events";

    @Transactional
    public Payment processPaymentRequest(String idempotencyKey, PaymentRequest request) {
        // Check if we already received this exact request (Idempotency check)
        paymentRepository.findByIdempotencyKey(idempotencyKey).ifPresent(payment -> {
            throw new IllegalArgumentException("Duplicate request. Payment already processing.");
        });

        // Save initial PENDING state to database
        Payment payment = Payment.builder()
                .idempotencyKey(idempotencyKey)
                .sourceAccount(request.sourceAccount())
                .destinationAccount(request.destinationAccount())
                .amount(request.amount())
                .status(PaymentStatus.PENDING)
                .build();

        Payment savedPayment = paymentRepository.save(payment);
        log.info("Saved PENDING payment with ID: {}", savedPayment.getId());

        // Publish to Kafka for asynchronous processing
        PaymentEvent event = new PaymentEvent(
                savedPayment.getId(),
                savedPayment.getIdempotencyKey(),
                savedPayment.getSourceAccount(),
                savedPayment.getDestinationAccount(),
                savedPayment.getAmount()
        );

        kafkaTemplate.send(TOPIC, savedPayment.getIdempotencyKey(), event);
        log.info("Published PaymentEvent to Kafka topic: {}", TOPIC);
        return savedPayment;
    }
}

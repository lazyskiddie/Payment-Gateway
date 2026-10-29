package com.event.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {

    static final String TOPIC = "payment-events";
    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]{1,64}$");

    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    @Value("${payment.reconcile.stale-after-seconds:60}")
    private long staleAfterSeconds;

    public Payment processPaymentRequest(String idempotencyKey, PaymentRequest request) {
        validate(idempotencyKey, request);
        String hash = fingerprint(request);

        var existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), hash);
        }

        Payment saved;
        try {
            saved = paymentRepository.saveAndFlush(Payment.builder()
                    .idempotencyKey(idempotencyKey)
                    .requestHash(hash)
                    .sourceAccount(request.sourceAccount().trim())
                    .destinationAccount(request.destinationAccount().trim())
                    .amount(request.amount())
                    .status(PaymentStatus.PENDING)
                    .build());
        } catch (DataIntegrityViolationException race) {
            Payment winner = paymentRepository.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> race);
            return replay(winner, hash);
        }

        log.info("Saved PENDING payment id={}", saved.getId());
        publish(saved);
        return saved;
    }

    public Payment findById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment " + id + " not found"));
    }

    @Scheduled(fixedDelayString = "${payment.reconcile.interval-ms:30000}")
    public void republishStalePending() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(staleAfterSeconds);
        var stale = paymentRepository.findTop100ByStatusAndCreatedAtBeforeOrderByIdAsc(PaymentStatus.PENDING, cutoff);
        if (!stale.isEmpty()) {
            log.warn("Re-publishing {} stale PENDING payments", stale.size());
            stale.forEach(this::publish);
        }
    }

    private void publish(Payment p) {
        PaymentEvent event = new PaymentEvent(p.getId(), p.getIdempotencyKey(),
                p.getSourceAccount(), p.getDestinationAccount(), p.getAmount());
        kafkaTemplate.send(TOPIC, p.getIdempotencyKey(), event).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Kafka publish failed for payment {}; reconciler will retry", p.getId(), ex);
            }
        });
    }

    private Payment replay(Payment existing, String hash) {
        if (existing.getRequestHash() != null && !existing.getRequestHash().equals(hash)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Idempotency-Key was already used with a different request body");
        }
        return existing;
    }

    private void validate(String key, PaymentRequest request) {
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must be 1-64 chars of letters, digits, '.', '_', ':' or '-'");
        }
        if (request.sourceAccount().trim().equalsIgnoreCase(request.destinationAccount().trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source and destination accounts must differ");
        }
    }

    static String fingerprint(PaymentRequest r) {
        String canonical = r.sourceAccount().trim() + "|" + r.destinationAccount().trim() + "|"
                + r.amount().stripTrailingZeros().toPlainString();
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

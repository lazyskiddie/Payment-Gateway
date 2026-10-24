package com.event.payment;

import jakarta.persistence.Entity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
public class Payment {
    private Long id;
    private String idempotencyKey;
    private String sourceAccount;
    private String destinationAccount;
    private BigDecimal amount;
    private PaymentStatus status;
    private LocalDateTime createdAt;
}

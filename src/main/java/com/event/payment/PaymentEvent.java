package com.event.payment;

import java.math.BigDecimal;

public record PaymentEvent(
        Long paymentId,
        String idempotencyKey,
        String sourceAccount,
        String destinationAccount,
        BigDecimal amount
) {}

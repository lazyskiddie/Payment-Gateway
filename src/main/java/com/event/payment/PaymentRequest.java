package com.event.payment;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record PaymentRequest(
        @NotBlank(message = "Source account is required") String sourceAccount,
        @NotBlank(message = "Destination account is required") String destinationAccount,
        @Positive(message = "Amount must be greater than zero") BigDecimal amount
) {}

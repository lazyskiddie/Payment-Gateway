package com.event.payment;

import java.math.BigDecimal;

import jakarta.validation.constraints.*;

public record PaymentRequest(
        @NotBlank(message = "Source account is required")
        @Size(max = 64, message = "Source account must be at most 64 characters") String sourceAccount,

        @NotBlank(message = "Destination account is required")
        @Size(max = 64, message = "Destination account must be at most 64 characters") String destinationAccount,

        // @Positive alone lets null through; @NotNull closes that hole
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        @Digits(integer = 15, fraction = 2, message = "Amount must have at most 2 decimal places") BigDecimal amount
) {}

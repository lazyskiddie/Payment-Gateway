package com.event.payment;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/event/payment")
@RequiredArgsConstructor
public class PaymentController {

    public final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<String> submitPayment(@RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) {

        paymentService.processPaymentRequest(idempotencyKey, request);
        return ResponseEntity.accepted().body("Payment accepted for processing");
    }
}

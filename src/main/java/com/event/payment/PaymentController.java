package com.event.payment;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/event/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    public record PaymentResponse(Long paymentId, PaymentStatus status, String failureReason) {
        static PaymentResponse from(Payment p) {
            return new PaymentResponse(p.getId(), p.getStatus(), p.getFailureReason());
        }
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> submitPayment(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                                         @Valid @RequestBody PaymentRequest request) {

        Payment payment = paymentService.processPaymentRequest(idempotencyKey, request);
        return ResponseEntity.accepted().body(PaymentResponse.from(payment));
    }

    @GetMapping("/{id}")
    public PaymentResponse getPayment(@PathVariable Long id) {
        return PaymentResponse.from(paymentService.findById(id));
    }
}

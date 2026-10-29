package com.event.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findTop100ByStatusAndCreatedAtBeforeOrderByIdAsc(PaymentStatus status, LocalDateTime before);

    @Modifying
    @Transactional
    @Query("update Payment p set p.status = com.event.payment.PaymentStatus.COMPLETED, p.processedAt = :now "
            + "where p.id = :id and p.status = com.event.payment.PaymentStatus.PENDING")
    int markCompleted(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Modifying
    @Transactional
    @Query("update Payment p set p.status = com.event.payment.PaymentStatus.FAILED, p.failureReason = :reason, "
            + "p.processedAt = :now where p.id = :id and p.status = com.event.payment.PaymentStatus.PENDING")
    int markFailed(@Param("id") Long id, @Param("reason") String reason, @Param("now") LocalDateTime now);
}

package com.esign.payment.repository;

import com.esign.payment.model.Payment;
import com.esign.payment.model.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Page<Payment> findByUserId(UUID userId, Pageable pageable);
    List<Payment> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, PaymentStatus status);
    Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId);
    List<Payment> findByUserIdAndUpdatedAtAfterOrderByUpdatedAtDesc(UUID userId, LocalDateTime since);
}

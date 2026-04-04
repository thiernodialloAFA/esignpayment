package com.esign.payment.repository;

import com.esign.payment.model.AccountApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountApplicationRepository extends JpaRepository<AccountApplication, UUID> {
    List<AccountApplication> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Page<AccountApplication> findByUserId(UUID userId, Pageable pageable);
    Optional<AccountApplication> findByReferenceNumber(String referenceNumber);
    Optional<AccountApplication> findByContractDocumentId(UUID contractDocumentId);
    List<AccountApplication> findByUserIdAndUpdatedAtAfterOrderByUpdatedAtDesc(UUID userId, LocalDateTime since);
}


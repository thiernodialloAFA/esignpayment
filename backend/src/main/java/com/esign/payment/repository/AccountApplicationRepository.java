package com.esign.payment.repository;

import com.esign.payment.model.AccountApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountApplicationRepository extends JpaRepository<AccountApplication, UUID> {
    List<AccountApplication> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<AccountApplication> findByReferenceNumber(String referenceNumber);
    Optional<AccountApplication> findByContractDocumentId(UUID contractDocumentId);
}


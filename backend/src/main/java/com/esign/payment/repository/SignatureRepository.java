package com.esign.payment.repository;

import com.esign.payment.model.Signature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SignatureRepository extends JpaRepository<Signature, UUID> {
    List<Signature> findByDocumentId(UUID documentId);
    boolean existsByDocumentIdAndSignerId(UUID documentId, UUID signerId);
}

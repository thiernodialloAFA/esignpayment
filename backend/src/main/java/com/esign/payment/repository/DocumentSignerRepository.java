package com.esign.payment.repository;

import com.esign.payment.model.DocumentSigner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentSignerRepository extends JpaRepository<DocumentSigner, UUID> {
    List<DocumentSigner> findByDocumentId(UUID documentId);
    Optional<DocumentSigner> findBySignatureToken(String token);
    List<DocumentSigner> findByEmail(String email);
}

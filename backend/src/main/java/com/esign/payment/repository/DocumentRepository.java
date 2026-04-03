package com.esign.payment.repository;

import com.esign.payment.model.Document;
import com.esign.payment.model.enums.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
    List<Document> findByOwnerIdAndStatusOrderByCreatedAtDesc(UUID ownerId, DocumentStatus status);
}

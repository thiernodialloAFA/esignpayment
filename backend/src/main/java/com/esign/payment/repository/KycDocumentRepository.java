package com.esign.payment.repository;

import com.esign.payment.model.KycDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface KycDocumentRepository extends JpaRepository<KycDocument, UUID> {
    List<KycDocument> findByApplicationId(UUID applicationId);
}


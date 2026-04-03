package com.esign.payment.repository;

import com.esign.payment.model.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountTypeRepository extends JpaRepository<AccountType, UUID> {
    Optional<AccountType> findByCode(String code);
    List<AccountType> findByIsActiveTrue();
}


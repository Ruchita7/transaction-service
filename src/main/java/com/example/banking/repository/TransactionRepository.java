package com.example.banking.repository;

import com.example.banking.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findByTransferFromAccountNumber(String accountNumber);
    List<Transaction> findByTransferToAccountNumber(String accountNumber);
}

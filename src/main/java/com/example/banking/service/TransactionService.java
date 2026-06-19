package com.example.banking.service;

import com.example.banking.dto.TransactionDTO;

import java.util.List;
import java.util.UUID;

public interface TransactionService {

    TransactionDTO findById(UUID transactionId);

    List<TransactionDTO> getTransactionHistory(String accountId);

    TransactionDTO processTransaction(TransactionDTO transactionDTO);

}

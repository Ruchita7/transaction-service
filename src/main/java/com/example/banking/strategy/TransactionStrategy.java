package com.example.banking.strategy;

import com.example.banking.dto.TransactionDTO;

public interface TransactionStrategy {

    TransactionDTO execute(TransactionDTO transactionDTO);
}

package org.example.banking.strategy;

import org.example.banking.dto.TransactionDTO;

public interface TransactionStrategy {

    TransactionDTO execute(TransactionDTO transactionDTO);
}

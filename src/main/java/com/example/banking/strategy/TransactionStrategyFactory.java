package com.example.banking.strategy;

import com.example.banking.dto.TransactionType;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
public class TransactionStrategyFactory {

    private DepositStrategy depositStrategy;
    private WithdrawStrategy withdrawStrategy;
    private TransferStrategy transferStrategy;

    public TransactionStrategy getStrategy(TransactionType transactionType) {
        return switch (transactionType) {
            case CREDIT -> depositStrategy;
            case DEBIT -> withdrawStrategy;
            case FUNDS_TRANSFER -> transferStrategy;
            default -> throw new IllegalArgumentException("Invalid transaction type: " + transactionType);
        };
    }
}

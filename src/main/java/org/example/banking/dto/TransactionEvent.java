package org.example.banking.dto;


import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor


public class TransactionEvent {

    private UUID transactionId;
    private String transferFromAccountNumber;
    private String transferToAccountNumber;
    private TransactionType transactionType;
    private BigDecimal amount;
    private String description;
    private TransactionStatus status;
    private String referenceId;
}

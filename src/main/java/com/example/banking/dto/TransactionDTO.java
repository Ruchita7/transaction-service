package com.example.banking.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class TransactionDTO {

    private UUID transactionId;
    private String transferFromAccountNumber;
    private String transferToAccountNumber;
    private TransactionType transactionType;
    private BigDecimal amount;
    private String description;
    private TransactionStatus status;
    private LocalDateTime createdOn;
    private LocalDateTime updatedOn;
    private String referenceId;
}

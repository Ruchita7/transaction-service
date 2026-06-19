package com.example.banking.entity;

import com.example.banking.dto.TransactionStatus;
import com.example.banking.dto.TransactionType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
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

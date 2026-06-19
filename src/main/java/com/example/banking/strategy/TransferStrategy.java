package com.example.banking.strategy;

import com.example.banking.AccountServiceClient;
import com.example.banking.dto.*;
import com.example.banking.entity.Transaction;
import com.example.banking.repository.TransactionRepository;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Component
public class TransferStrategy implements TransactionStrategy {


    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountServiceClient accountServiceClient;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    @Override
    @Transactional
    public TransactionDTO execute(TransactionDTO transactionDTO) {

        AccountDTO depositToAccount = accountServiceClient.getAccountByNumber(transactionDTO.getTransferToAccountNumber());
        AccountDTO withdrawFromAccount = accountServiceClient.getAccountByNumber(transactionDTO.getTransferFromAccountNumber());
        if (Objects.isNull(depositToAccount)) {
            throw new RuntimeException("Deposit to account does not exist");
        }
        if (Objects.isNull(withdrawFromAccount)) {
            throw new RuntimeException("Withdraw account does not exist");
        }

        Transaction transaction = modelMapper.map(transactionDTO, Transaction.class);
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setCreatedOn(LocalDateTime.now());
        transaction.setReferenceId(UUID.randomUUID().toString());
        transactionRepository.save(transaction);

        try {
            accountServiceClient.withdrawAccount(transactionDTO.getTransferFromAccountNumber(), new AmountRequest(transactionDTO.getAmount()));
            accountServiceClient.depositAccount(transactionDTO.getTransferToAccountNumber(), new AmountRequest(transactionDTO.getAmount()));

            transaction.setStatus(TransactionStatus.COMPLETED);
            Transaction savedTransaction = transactionRepository.save(transaction);
            kafkaTemplate.send("transactions", TransactionEvent.builder()
                    .transactionId(savedTransaction.getTransactionId())
                    .transactionType(transactionDTO.getTransactionType())
                    .amount(savedTransaction.getAmount())
                    .transferToAccountNumber(savedTransaction.getTransferToAccountNumber())
                    .transferFromAccountNumber(savedTransaction.getTransferFromAccountNumber())
                    .referenceId(savedTransaction.getReferenceId())
                    .status(TransactionStatus.COMPLETED)
                    .build());
            return modelMapper.map(savedTransaction, TransactionDTO.class);

        } catch (Exception e) {
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);
            kafkaTemplate.send("transactions", TransactionEvent.builder()
                    .transactionId(transaction.getTransactionId())
                    .transactionType(transactionDTO.getTransactionType())
                    .amount(transaction.getAmount())
                    .transferToAccountNumber(transaction.getTransferToAccountNumber())
                    .transferFromAccountNumber(transaction.getTransferFromAccountNumber())
                    .referenceId(transaction.getReferenceId())
                    .status(TransactionStatus.FAILED)
                    .build());
            throw new RuntimeException("Deposit failed: " + e.getMessage());
        }
    }
}

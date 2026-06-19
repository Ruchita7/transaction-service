package com.example.banking.strategy;

import com.example.banking.AccountServiceClient;
import com.example.banking.dto.*;
import com.example.banking.entity.Transaction;
import com.example.banking.repository.TransactionRepository;
import org.springframework.transaction.annotation.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Component
public class DepositStrategy implements TransactionStrategy {

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
        if (Objects.isNull(depositToAccount)) {
            throw new RuntimeException("Deposit to account does not exist");
        }

        Transaction transaction = modelMapper.map(transactionDTO, Transaction.class);
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setCreatedOn(LocalDateTime.now());
        transaction.setReferenceId(UUID.randomUUID().toString());
        Transaction pendingTransaction = transactionRepository.save(transaction);
        UUID transactionId = pendingTransaction.getTransactionId();
        try {
            accountServiceClient.depositAccount(transactionDTO.getTransferToAccountNumber(), new AmountRequest(transactionDTO.getAmount()));

          //  Transaction toComplete = transactionRepository.findById(transactionId)
            //        .orElseThrow(() -> new RuntimeException("Transaction not found: " + transactionId));

            pendingTransaction.setStatus(TransactionStatus.COMPLETED);
            Transaction savedTransaction = transactionRepository.save(pendingTransaction);
            kafkaTemplate.send("transactions", TransactionEvent.builder()
                    .transactionId(transactionId)
                    .transactionType(transactionDTO.getTransactionType())
                    .amount(savedTransaction.getAmount())
                    .transferToAccountNumber(savedTransaction.getTransferToAccountNumber())
                    .referenceId(savedTransaction.getReferenceId())
                    .status(TransactionStatus.COMPLETED)
                    .build());
            return modelMapper.map(savedTransaction, TransactionDTO.class);

        } catch (Exception e) {
        //    Transaction toFail = transactionRepository.findById(transactionId)
        //            .orElse(transaction);   // fallback if somehow not found
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);
            kafkaTemplate.send("transactions", TransactionEvent.builder()
                    .transactionId(transaction.getTransactionId())
                    .transactionType(transactionDTO.getTransactionType())
                    .amount(transaction.getAmount())
                    .transferToAccountNumber(transaction.getTransferToAccountNumber())
                    .referenceId(transaction.getReferenceId())
                    .status(TransactionStatus.FAILED)
                    .build());
            throw new RuntimeException("Deposit failed: " + e.getMessage());
        }

    }
}

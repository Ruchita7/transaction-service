package org.example.banking.strategy;

import jakarta.persistence.EntityNotFoundException;
import org.example.banking.AccountServiceClient;
import org.example.banking.dto.*;
import org.example.banking.entity.Transaction;
import org.example.banking.exception.TransactionFailedException;
import org.example.banking.repository.TransactionRepository;
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

    // NOTE: intentionally NOT @Transactional — see WithdrawStrategy.

    @Override
    public TransactionDTO execute(TransactionDTO transactionDTO) {

        AccountDTO depositToAccount =
                accountServiceClient.getAccountByNumber(transactionDTO.getTransferToAccountNumber());
        if (Objects.isNull(depositToAccount)) {
            throw new EntityNotFoundException(
                    "Deposit to account does not exist: " + transactionDTO.getTransferToAccountNumber());
        }

        Transaction transaction = modelMapper.map(transactionDTO, Transaction.class);
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setCreatedOn(LocalDateTime.now());
        transaction.setReferenceId(UUID.randomUUID().toString());
        transactionRepository.save(transaction);

        try {
            accountServiceClient.depositAccount(
                    transactionDTO.getTransferToAccountNumber(),
                    new AmountRequest(transactionDTO.getAmount()),
                    transaction.getReferenceId() + "-deposit");

            transaction.setStatus(TransactionStatus.COMPLETED);
            Transaction savedTransaction = transactionRepository.save(transaction);
            emitEvent(savedTransaction, transactionDTO, TransactionStatus.COMPLETED);
            return modelMapper.map(savedTransaction, TransactionDTO.class);

        } catch (EntityNotFoundException | IllegalStateException e) {
            recordFailure(transaction, transactionDTO, TransactionStatus.FAILED);
            throw e;
        } catch (Exception e) {
            recordFailure(transaction, transactionDTO, TransactionStatus.FAILED);
            throw new TransactionFailedException(
                    "Deposit failed for account " + transactionDTO.getTransferToAccountNumber(), e);
        }
    }

    private void recordFailure(Transaction transaction, TransactionDTO transactionDTO, TransactionStatus status) {
        transaction.setStatus(status);
        transactionRepository.save(transaction);
        emitEvent(transaction, transactionDTO, status);
    }

    private void emitEvent(Transaction transaction, TransactionDTO transactionDTO, TransactionStatus status) {
        kafkaTemplate.send("transactions", TransactionEvent.builder()
                .transactionId(transaction.getTransactionId())
                .transactionType(transactionDTO.getTransactionType())
                .amount(transaction.getAmount())
                .transferToAccountNumber(transaction.getTransferToAccountNumber())
                .referenceId(transaction.getReferenceId())
                .status(status)
                .build());
    }
}

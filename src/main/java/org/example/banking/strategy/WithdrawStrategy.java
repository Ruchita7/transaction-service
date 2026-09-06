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
public class WithdrawStrategy implements TransactionStrategy {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountServiceClient accountServiceClient;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    // NOTE: intentionally NOT @Transactional. execute() persists PENDING, then FAILED
    // on error before rethrowing — a surrounding transaction would roll both back, so
    // the DB would show nothing while a FAILED event was already published to Kafka.

    @Override
    public TransactionDTO execute(TransactionDTO transactionDTO) {

        AccountDTO withdrawFromAccount =
                accountServiceClient.getAccountByNumber(transactionDTO.getTransferFromAccountNumber());
        if (Objects.isNull(withdrawFromAccount)) {
            throw new EntityNotFoundException(
                    "Withdraw from account does not exist: " + transactionDTO.getTransferFromAccountNumber());
        }

        Transaction transaction = modelMapper.map(transactionDTO, Transaction.class);
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setCreatedOn(LocalDateTime.now());
        transaction.setReferenceId(UUID.randomUUID().toString());
        transactionRepository.save(transaction);

        try {
            accountServiceClient.withdrawAccount(transactionDTO.getTransferFromAccountNumber(),
                    new AmountRequest(transactionDTO.getAmount()));

            transaction.setStatus(TransactionStatus.COMPLETED);
            Transaction savedTransaction = transactionRepository.save(transaction);
            emitEvent(savedTransaction, transactionDTO, TransactionStatus.COMPLETED);
            return modelMapper.map(savedTransaction, TransactionDTO.class);

        } catch (EntityNotFoundException | IllegalStateException e) {
            // expected failure (account vanished / insufficient balance) — record it,
            // then let it propagate so it maps to 404 / 409, not 502.
            recordFailure(transaction, transactionDTO);
            throw e;
        } catch (Exception e) {
            recordFailure(transaction, transactionDTO);
            throw new TransactionFailedException(
                    "Withdraw failed for account " + transactionDTO.getTransferFromAccountNumber(), e);
        }
    }

    private void recordFailure(Transaction transaction, TransactionDTO transactionDTO) {
        transaction.setStatus(TransactionStatus.FAILED);
        transactionRepository.save(transaction);
        emitEvent(transaction, transactionDTO, TransactionStatus.FAILED);
    }

    private void emitEvent(Transaction transaction, TransactionDTO transactionDTO, TransactionStatus status) {
        kafkaTemplate.send("transactions", TransactionEvent.builder()
                .transactionId(transaction.getTransactionId())
                .transactionType(transactionDTO.getTransactionType())
                .amount(transaction.getAmount())
                .transferFromAccountNumber(transaction.getTransferFromAccountNumber())
                .referenceId(transaction.getReferenceId())
                .status(status)
                .build());
    }
}

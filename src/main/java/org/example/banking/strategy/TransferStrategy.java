package org.example.banking.strategy;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.example.banking.AccountServiceClient;
import org.example.banking.dto.*;
import org.example.banking.entity.Transaction;
import org.example.banking.exception.TransactionFailedException;
import org.example.banking.repository.TransactionRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Component
@Slf4j
public class TransferStrategy implements TransactionStrategy {

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
        AccountDTO withdrawFromAccount =
                accountServiceClient.getAccountByNumber(transactionDTO.getTransferFromAccountNumber());
        if (Objects.isNull(depositToAccount)) {
            throw new EntityNotFoundException(
                    "Deposit to account does not exist: " + transactionDTO.getTransferToAccountNumber());
        }
        if (Objects.isNull(withdrawFromAccount)) {
            throw new EntityNotFoundException(
                    "Withdraw account does not exist: " + transactionDTO.getTransferFromAccountNumber());
        }

        Transaction transaction = modelMapper.map(transactionDTO, Transaction.class);
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setCreatedOn(LocalDateTime.now());
        transaction.setReferenceId(UUID.randomUUID().toString());
        transactionRepository.save(transaction);

        String refId = transaction.getReferenceId();
        String fromAccount = transactionDTO.getTransferFromAccountNumber();
        String toAccount = transactionDTO.getTransferToAccountNumber();
        BigDecimal amount = transactionDTO.getAmount();
        boolean withdrawn = false;

        try {
            accountServiceClient.withdrawAccount(fromAccount, new AmountRequest(amount), refId + "-withdraw");
            withdrawn = true;
            accountServiceClient.depositAccount(toAccount, new AmountRequest(amount), refId + "-deposit");

            transaction.setStatus(TransactionStatus.COMPLETED);
            Transaction savedTransaction = transactionRepository.save(transaction);
            emitEvent(savedTransaction, transactionDTO, TransactionStatus.COMPLETED);
            return modelMapper.map(savedTransaction, TransactionDTO.class);

        } catch (EntityNotFoundException | IllegalStateException e) {
            recordFailure(transaction, transactionDTO, compensate(withdrawn, fromAccount, amount, refId));
            throw e;
        } catch (Exception e) {
            recordFailure(transaction, transactionDTO, compensate(withdrawn, fromAccount, amount, refId));
            throw new TransactionFailedException("Transfer failed from " + fromAccount + " to " + toAccount, e);
        }
    }

    /**
     * If the withdraw leg already succeeded but the deposit leg did not, credit the amount
     * back to the source account (compensating action). The {@code -compensate} idempotency
     * key keeps this safe to repeat.
     *
     * @return {@code FAILED} if nothing moved or the money was returned;
     *         {@code NEEDS_RECONCILIATION} if the compensating credit itself failed —
     *         funds have left the source account and require manual repair.
     */
    private TransactionStatus compensate(boolean withdrawn, String fromAccount, BigDecimal amount, String refId) {
        if (!withdrawn) {
            return TransactionStatus.FAILED;
        }
        try {
            accountServiceClient.depositAccount(fromAccount, new AmountRequest(amount), refId + "-compensate");
            log.warn("Transfer {} rolled back: {} credited back to {}", refId, amount, fromAccount);
            return TransactionStatus.FAILED;
        } catch (Exception comp) {
            log.error("COMPENSATION FAILED for transfer {} — {} debited from {} was NOT returned; "
                    + "manual reconciliation required", refId, amount, fromAccount, comp);
            return TransactionStatus.NEEDS_RECONCILIATION;
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
                .transferFromAccountNumber(transaction.getTransferFromAccountNumber())
                .referenceId(transaction.getReferenceId())
                .status(status)
                .build());
    }
}

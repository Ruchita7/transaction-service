package org.example.banking.service;

import jakarta.persistence.EntityNotFoundException;
import org.example.banking.dto.TransactionDTO;
import org.example.banking.entity.Transaction;
import org.example.banking.repository.TransactionRepository;
import org.example.banking.strategy.TransactionStrategyFactory;
import lombok.AllArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@AllArgsConstructor
public class TransactionService_Impl implements TransactionService {


    private  TransactionStrategyFactory transactionStrategyFactory;
    private  TransactionRepository transactionRepository;
    private ModelMapper mapper;

    @Override
    public TransactionDTO findById(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found: " + transactionId));
        return mapper.map(transaction, TransactionDTO.class);
    }

    @Override
    public List<TransactionDTO> getTransactionHistory(String accountNumber) {
        return Stream.concat(
                        transactionRepository.findByTransferFromAccountNumber(accountNumber).stream(),
                        transactionRepository.findByTransferToAccountNumber(accountNumber).stream())
                .map(t -> mapper.map(t, TransactionDTO.class))
                .toList();
    }

    @Override
    public TransactionDTO processTransaction(TransactionDTO transactionDTO) {
        return transactionStrategyFactory.getStrategy(transactionDTO.getTransactionType()).execute(transactionDTO);
    }

}

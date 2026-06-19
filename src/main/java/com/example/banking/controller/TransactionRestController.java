package com.example.banking.controller;

import com.example.banking.dto.AmountRequest;
import com.example.banking.dto.TransactionDTO;
import com.example.banking.service.TransactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/transactions")
public class TransactionRestController {

    private TransactionService transactionService;

    public TransactionRestController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionDTO> getTransactionById(@PathVariable(name = "transactionId") UUID transactionId) {
        return ResponseEntity.ok(transactionService.findById(transactionId));
    }

    @GetMapping("/account/{accountNumber}")
    public ResponseEntity<List<TransactionDTO>> getTransactionsByAccountNumber(@PathVariable(name = "accountNumber")
                                                                               String accountNumber) {
        return ResponseEntity.ok(transactionService.getTransactionHistory(accountNumber));
    }


    @PostMapping("/transfer")
    public ResponseEntity<TransactionDTO> transferFunds(@RequestBody  TransactionDTO transactionDTO) {
        return ResponseEntity.ok(transactionService.processTransaction(transactionDTO));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<TransactionDTO> withdraw(@RequestBody TransactionDTO transactionDTO) {
        return ResponseEntity.ok(transactionService.processTransaction(transactionDTO));
    }

    @PostMapping("/deposit")
    public ResponseEntity<TransactionDTO> deposit(@RequestBody TransactionDTO transactionDTO) {
        return ResponseEntity.ok(transactionService.processTransaction(transactionDTO));
    }
}

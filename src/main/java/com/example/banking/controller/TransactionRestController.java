package com.example.banking.controller;

import com.example.banking.dto.AmountRequest;
import com.example.banking.dto.TransactionDTO;
import com.example.banking.service.TransactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/transactions")
@Tag(name = "Transaction Management", description = "APIs for managing application transactions")
public class TransactionRestController {

    private TransactionService transactionService;

    public TransactionRestController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping("/{transactionId}")
    @Operation(
            summary = "Get transaction by ID",
            description = "Provides full transaction details based on the unique transaction database ID."
    )
    @ApiResponse(responseCode = "200", description = "Transaction successfully found")
    @ApiResponse(responseCode = "404", description = "Transaction not found")
    public ResponseEntity<TransactionDTO> getTransactionById(@PathVariable(name = "transactionId") UUID transactionId) {
        return ResponseEntity.ok(transactionService.findById(transactionId));
    }

    @GetMapping("/account/{accountNumber}")
    @Operation(
            summary = "Get transaction by account",
            description = "Provides full transaction details based on the unique account number."
    )
    @ApiResponse(responseCode = "200", description = "Transaction successfully found")
    @ApiResponse(responseCode = "404", description = "Transaction not found")
    public ResponseEntity<List<TransactionDTO>> getTransactionsByAccountNumber(@PathVariable(name = "accountNumber")
                                                                               String accountNumber) {
        return ResponseEntity.ok(transactionService.getTransactionHistory(accountNumber));
    }


    @PostMapping("/transfer")
    @Operation(
            summary = "Transfer",
            description = "Perform transfer of funds."
    )
    @ApiResponse(responseCode = "200", description = "Transaction successfully performed")
    @ApiResponse(responseCode = "404", description = "Transaction not performed")
    public ResponseEntity<TransactionDTO> transferFunds(@RequestBody  TransactionDTO transactionDTO) {
        return ResponseEntity.ok(transactionService.processTransaction(transactionDTO));
    }

    @PostMapping("/withdraw")
    @Operation(
            summary = "Withdrew",
            description = "Perform withdrawal of funds."
    )
    @ApiResponse(responseCode = "200", description = "Withdrawal successfully performed")
    @ApiResponse(responseCode = "404", description = "Withdrawal not performed")
    public ResponseEntity<TransactionDTO> withdraw(@RequestBody TransactionDTO transactionDTO) {
        return ResponseEntity.ok(transactionService.processTransaction(transactionDTO));
    }

    @PostMapping("/deposit")
    @Operation(
            summary = "Deposit",
            description = "Perform deposit of funds."
    )
    @ApiResponse(responseCode = "200", description = "Deposit successfully performed")
    @ApiResponse(responseCode = "404", description = "Deposit not performed")
    public ResponseEntity<TransactionDTO> deposit(@RequestBody TransactionDTO transactionDTO) {
        return ResponseEntity.ok(transactionService.processTransaction(transactionDTO));
    }
}

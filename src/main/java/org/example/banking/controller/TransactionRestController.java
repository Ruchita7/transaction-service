package org.example.banking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.banking.dto.ApiError;
import org.example.banking.dto.TransactionDTO;
import org.example.banking.service.TransactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/transactions")
@Tag(name = "Transaction Management", description = "APIs for managing application transactions")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Malformed request or invalid transaction type",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected server error",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class TransactionRestController {

    private final TransactionService transactionService;

    public TransactionRestController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "Get transaction by ID",
            description = "Provides full transaction details based on the unique transaction database ID.")
    @ApiResponse(responseCode = "200", description = "Transaction found")
    @ApiResponse(responseCode = "404", description = "Transaction not found",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<TransactionDTO> getTransactionById(@PathVariable(name = "transactionId") UUID transactionId) {
        return ResponseEntity.ok(transactionService.findById(transactionId));
    }

    @GetMapping("/account/{accountNumber}")
    @Operation(summary = "Get transaction history by account",
            description = "Returns every transaction the account took part in; an empty list if it has none.")
    @ApiResponse(responseCode = "200", description = "Transaction history (possibly empty)")
    public ResponseEntity<List<TransactionDTO>> getTransactionsByAccountNumber(
            @PathVariable(name = "accountNumber") String accountNumber) {
        return ResponseEntity.ok(transactionService.getTransactionHistory(accountNumber));
    }

    @PostMapping("/transfer")
    @Operation(summary = "Transfer", description = "Move funds from one account to another.")
    @ApiResponse(responseCode = "200", description = "Transfer completed")
    @ApiResponse(responseCode = "404", description = "Source or destination account not found",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Insufficient balance",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "502", description = "Downstream account-service failure",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<TransactionDTO> transferFunds(@RequestBody TransactionDTO transactionDTO) {
        return ResponseEntity.ok(transactionService.processTransaction(transactionDTO));
    }

    @PostMapping("/withdraw")
    @Operation(summary = "Withdraw", description = "Withdraw funds from an account.")
    @ApiResponse(responseCode = "200", description = "Withdrawal completed")
    @ApiResponse(responseCode = "404", description = "Account not found",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Insufficient balance",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "502", description = "Downstream account-service failure",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<TransactionDTO> withdraw(@RequestBody TransactionDTO transactionDTO) {
        return ResponseEntity.ok(transactionService.processTransaction(transactionDTO));
    }

    @PostMapping("/deposit")
    @Operation(summary = "Deposit", description = "Deposit funds into an account.")
    @ApiResponse(responseCode = "200", description = "Deposit completed")
    @ApiResponse(responseCode = "404", description = "Account not found",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "502", description = "Downstream account-service failure",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<TransactionDTO> deposit(@RequestBody TransactionDTO transactionDTO) {
        return ResponseEntity.ok(transactionService.processTransaction(transactionDTO));
    }
}

package org.example.banking;

import org.example.banking.exception.TransactionFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Caller sent something malformed — bad value, missing/blank param. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        log.warn("400 Bad Request — {}", ex.getMessage());
        return ResponseEntity.badRequest().body(errorBody(ex.getMessage()));
    }

    /** Path/query value could not be converted to the expected type, e.g. a non-UUID id. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value for '" + ex.getName() + "': " + ex.getValue();
        log.warn("400 Bad Request — {}", message);
        return ResponseEntity.badRequest().body(errorBody(message));
    }

    /** The requested resource does not exist. */
    @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(jakarta.persistence.EntityNotFoundException ex) {
        log.warn("404 Not Found — {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(ex.getMessage()));
    }

    /** Input was valid but a business rule forbids the operation, e.g. insufficient balance. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException ex) {
        log.warn("409 Conflict — {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody(ex.getMessage()));
    }

    /** A well-formed transaction failed because a downstream dependency errored. */
    @ExceptionHandler(TransactionFailedException.class)
    public ResponseEntity<Map<String, Object>> handleTransactionFailed(TransactionFailedException ex) {
        log.error("502 Bad Gateway — {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorBody(ex.getMessage()));
    }

    /** Anything unmapped is a real server fault — log it with the stack trace, hide details from the client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("500 Internal Server Error — unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody("Internal server error"));
    }

    private Map<String, Object> errorBody(String message) {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "error", message
        );
    }
}

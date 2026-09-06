package org.example.banking.exception;

/**
 * Thrown when a transaction could not be completed because of a fault we do not
 * classify more specifically — typically a downstream call to user-account-service
 * failed for an unexpected reason. The transaction row is persisted with status
 * {@code FAILED} before this is thrown.
 *
 * <p>Expected outcomes are NOT represented by this type:
 * <ul>
 *   <li>account does not exist  → {@link jakarta.persistence.EntityNotFoundException} (404)</li>
 *   <li>insufficient balance    → {@link IllegalStateException} (409)</li>
 *   <li>invalid transaction type → {@link IllegalArgumentException} (400)</li>
 * </ul>
 * Maps to HTTP 502 in {@code GlobalExceptionHandler}.
 */
public class TransactionFailedException extends RuntimeException {

    public TransactionFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public TransactionFailedException(String message) {
        super(message);
    }
}

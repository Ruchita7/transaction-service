package org.example.banking.dto;

import java.time.Instant;

/** Standard error body returned by {@code GlobalExceptionHandler}. */
public record ApiError(String timestamp, String error) {

    public static ApiError of(String message) {
        return new ApiError(Instant.now().toString(), message);
    }
}

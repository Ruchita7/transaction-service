package org.example.banking.dto;

public enum TransactionStatus {

    PENDING("Pending"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    NEEDS_RECONCILIATION("Needs reconciliation");

    private String status;

    TransactionStatus(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }
}

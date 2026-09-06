package org.example.banking.dto;

public enum TransactionStatus {

    PENDING("Pending"),
    COMPLETED("Completed"),
    FAILED("Failed");

    private String status;

    TransactionStatus(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }
}

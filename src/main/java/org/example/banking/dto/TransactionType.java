package org.example.banking.dto;

public enum TransactionType {

    CREDIT("Credit"),
    DEBIT("Debit"),
    FUNDS_TRANSFER("Funds_Transfer");

    private String type;

    TransactionType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}

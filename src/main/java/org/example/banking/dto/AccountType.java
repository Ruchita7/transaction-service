package org.example.banking.dto;

/**
 * Enum representing the type of bank account.
 */
public enum AccountType {

    CURRENT("current"),
    SAVINGS("savings");

    private String type;

    AccountType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}

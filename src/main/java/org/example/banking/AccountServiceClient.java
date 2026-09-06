package org.example.banking;

import jakarta.persistence.EntityNotFoundException;
import org.example.banking.dto.AccountDTO;
import org.example.banking.dto.AmountRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;


@Component
public class AccountServiceClient {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${user.service.url}")
    private String userServiceUrl;

    public AccountDTO getAccountByNumber(String accountNumber) {
        String url = userServiceUrl + "/account/accountNo?accountNumber=" + accountNumber;
        try {
            return restTemplate.getForObject(url, AccountDTO.class);
        } catch (HttpClientErrorException.NotFound e) {
            // user-account-service returns 404 when the account is absent; getForObject
            // throws rather than returning null, so translate it back to null for callers.
            return null;
        }
    }

    public void depositAccount(String accountNumber, AmountRequest amountRequest) {
        String url = userServiceUrl + "/account/" + accountNumber + "/credit";
        try {
            restTemplate.put(url, amountRequest, AccountDTO.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new EntityNotFoundException("Deposit account does not exist: " + accountNumber);
        }
    }

    public void withdrawAccount(String accountNumber, AmountRequest amountRequest) {
        String url = userServiceUrl + "/account/" + accountNumber + "/debit";
        try {
            restTemplate.put(url, amountRequest, AccountDTO.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new EntityNotFoundException("Withdraw account does not exist: " + accountNumber);
        } catch (HttpClientErrorException.Conflict e) {
            // user-account-service returns 409 for a business-rule violation (insufficient balance)
            throw new IllegalStateException("Insufficient balance in account: " + accountNumber);
        }
    }
}

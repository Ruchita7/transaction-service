package org.example.banking;

import jakarta.persistence.EntityNotFoundException;
import org.example.banking.dto.AccountDTO;
import org.example.banking.dto.AmountRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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

    /**
     * @param idempotencyKey forwarded as the {@code Idempotency-Key} header so a retry or
     *                       a compensating call is applied at most once by user-account-service.
     */
    public void depositAccount(String accountNumber, AmountRequest amountRequest, String idempotencyKey) {
        String url = userServiceUrl + "/account/" + accountNumber + "/credit";
        try {
            restTemplate.exchange(url, HttpMethod.PUT, request(amountRequest, idempotencyKey), Void.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new EntityNotFoundException("Deposit account does not exist: " + accountNumber);
        }
    }

    public void withdrawAccount(String accountNumber, AmountRequest amountRequest, String idempotencyKey) {
        String url = userServiceUrl + "/account/" + accountNumber + "/debit";
        try {
            restTemplate.exchange(url, HttpMethod.PUT, request(amountRequest, idempotencyKey), Void.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new EntityNotFoundException("Withdraw account does not exist: " + accountNumber);
        } catch (HttpClientErrorException.Conflict e) {
            // user-account-service returns 409 for a business-rule violation (insufficient balance)
            throw new IllegalStateException("Insufficient balance in account: " + accountNumber);
        }
    }

    private HttpEntity<AmountRequest> request(AmountRequest body, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return new HttpEntity<>(body, headers);
    }
}

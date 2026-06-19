package com.example.banking;

import com.example.banking.dto.AccountDTO;
import com.example.banking.dto.AmountRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Component
public class AccountServiceClient {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${user.service.url}")
    private String userServiceUrl;

    public AccountDTO getAccountByNumber(String accountNumber) {
        String url = userServiceUrl + "/account/accountNo?accountNumber=" + accountNumber;
        return restTemplate.getForObject(url, AccountDTO.class);
    }

    public void depositAccount(String accountNumber, AmountRequest amountRequest) {
        String url = userServiceUrl + "/account/"+ accountNumber +"/credit" ;
        restTemplate.put(url, amountRequest, AccountDTO.class);
    }

    public void withdrawAccount(String accountNumber, AmountRequest amountRequest) {
        String url = userServiceUrl + "/account/"+ accountNumber +"/debit" ;
        restTemplate.put(url, amountRequest, AccountDTO.class);
    }
}

package com.example.banking;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

/*
    This is a configuration class for the application. It defines a bean for ModelMapper, which is a library used for
    mapping between different object models, such as between entities and DTOs.
 */
@Configuration
public class ApplicationConfig {

    @Value("${user.service.url}")
    private String userServiceUrl;

    @Bean
    public RestTemplate getRestTemplate() {
        return new RestTemplate();
    }

    @Bean
    public String getUserServiceUrl() {
        return userServiceUrl;
    }

    @Bean
    public ModelMapper modelMapper() {
        return new ModelMapper();
    }
}

package org.example.banking;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/*
    This is the main application class for a Spring Boot application. It is annotated with @SpringBootApplication, which is a convenience annotation that combines @Configuration, @EnableAutoConfiguration, and @ComponentScan. This annotation indicates that this class serves as the entry point for the Spring Boot application. The main method uses SpringApplication.run() to launch the application, passing in the class itself and any command-line arguments.
 */
@SpringBootApplication
public class SpringBootMainApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringBootMainApplication.class, args);
    }
}

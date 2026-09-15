package com.ildcvf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class IldcvfBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(IldcvfBackendApplication.class, args);
    }
}
package com.gte.argos.factory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FactoryCentralServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FactoryCentralServerApplication.class, args);
    }
}

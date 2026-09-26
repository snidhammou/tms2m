package com.tms.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TmsServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TmsServerApplication.class, args);
    }
}

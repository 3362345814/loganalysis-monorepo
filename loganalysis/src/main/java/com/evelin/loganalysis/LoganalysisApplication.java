package com.evelin.loganalysis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LoganalysisApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoganalysisApplication.class, args);
    }

}

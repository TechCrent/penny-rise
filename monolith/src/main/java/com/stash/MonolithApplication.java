package com.stash;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(
        scanBasePackages = {
            "com.stash",
            "com.stash.shared.apierrors"
        }
)
@EnableRetry
@EnableAsync
public class MonolithApplication {
    public static void main(String[] args) {
        SpringApplication.run(MonolithApplication.class, args);
    }
}
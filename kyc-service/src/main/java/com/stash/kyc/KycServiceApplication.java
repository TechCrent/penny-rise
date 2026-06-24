package com.stash.kyc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
        scanBasePackages = {
                "com.stash",
                "com.stash.shared.apierrors"
        },
        exclude = {
                org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
                org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration.class
        }
)
@EnableRetry
@EnableScheduling
public class KycServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(KycServiceApplication.class, args);
    }
}

package com.demo.onboarding;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TenantOnboardingApplication {

    public static void main(String[] args) {
        SpringApplication.run(TenantOnboardingApplication.class, args);
    }
}

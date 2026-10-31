package com.demo.onboarding.api;

import com.demo.onboarding.service.TenantOnboardingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UiConfigController {

    private final TenantOnboardingService service;

    public UiConfigController(TenantOnboardingService service) {
        this.service = service;
    }

    @GetMapping("/api/config")
    public UiConfig config() {
        return service.uiConfig();
    }
}

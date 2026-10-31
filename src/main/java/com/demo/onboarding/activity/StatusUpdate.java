package com.demo.onboarding.activity;

import com.demo.onboarding.domain.OnboardingStep;
import com.demo.onboarding.domain.TenantStatus;

public record StatusUpdate(
        String tenantId,
        TenantStatus status,
        OnboardingStep step,
        String message
) {
}

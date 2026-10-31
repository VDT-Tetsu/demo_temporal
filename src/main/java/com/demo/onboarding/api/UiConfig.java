package com.demo.onboarding.api;

import com.demo.onboarding.domain.OnboardingStep;

import java.util.List;

/** Cau hinh FE can biet: link Temporal UI, cac buoc cua flow, gia tri mac dinh. */
public record UiConfig(
        String temporalUiUrl,
        String temporalNamespace,
        List<OnboardingStep> steps,
        double defaultFailureRate,
        long stepDelayMinSeconds,
        long stepDelayMaxSeconds,
        long approvalTimeoutSeconds,
        long manualRetryTimeoutSeconds
) {
}

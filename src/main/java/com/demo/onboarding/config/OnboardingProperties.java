package com.demo.onboarding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param stepDelayMin thoi gian gia lap toi thieu cua 1 buoc (moi lan chay random trong [min, max])
 * @param stepDelayMax thoi gian gia lap toi da cua 1 buoc
 */
@ConfigurationProperties(prefix = "onboarding")
public record OnboardingProperties(
        String taskQueue,
        double failureRate,
        Duration stepDelayMin,
        Duration stepDelayMax,
        int activityMaxAttempts,
        Duration approvalTimeout,
        Duration manualRetryTimeout,
        String temporalUiUrl,
        String temporalNamespace
) {

    public OnboardingProperties {
        if (stepDelayMax.compareTo(stepDelayMin) < 0) {
            throw new IllegalArgumentException(
                    "onboarding.step-delay-max (" + stepDelayMax + ") must be >= step-delay-min (" + stepDelayMin + ")");
        }
    }
}

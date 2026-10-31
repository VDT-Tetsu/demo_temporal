package com.demo.onboarding.workflow;

/**
 * Input cua workflow. Cac gia tri cau hinh (timeout, retry) duoc dong goi vao input
 * vi workflow code phai deterministic, khong duoc doc truc tiep tu Spring config.
 */
public record OnboardingInput(
        String tenantId,
        String tenantName,
        String adminEmail,
        String plan,
        double failureRate,
        int activityMaxAttempts,
        long activityTimeoutSeconds,
        long approvalTimeoutSeconds,
        long manualRetryTimeoutSeconds
) {
}

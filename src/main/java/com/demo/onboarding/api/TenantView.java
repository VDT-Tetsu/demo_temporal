package com.demo.onboarding.api;

import com.demo.onboarding.domain.OnboardingStep;
import com.demo.onboarding.domain.Tenant;
import com.demo.onboarding.domain.TenantStatus;

import java.time.Instant;

/**
 * Tenant kem trang thai thuc thi tren Temporal.
 * {@code status} la business status (DB), {@code temporalStatus} la execution status
 * (RUNNING, COMPLETED, FAILED, ...; NOT_FOUND neu Temporal da xoa workflow do het retention,
 * UNKNOWN neu khong goi duoc Temporal).
 */
public record TenantView(
        String id,
        String name,
        String adminEmail,
        String plan,
        TenantStatus status,
        OnboardingStep currentStep,
        String message,
        String workflowId,
        String temporalStatus,
        Instant createdAt,
        Instant updatedAt
) {

    public static TenantView of(Tenant tenant, String temporalStatus) {
        return new TenantView(tenant.getId(), tenant.getName(), tenant.getAdminEmail(), tenant.getPlan(),
                tenant.getStatus(), tenant.getCurrentStep(), tenant.getMessage(), tenant.getWorkflowId(),
                temporalStatus, tenant.getCreatedAt(), tenant.getUpdatedAt());
    }
}

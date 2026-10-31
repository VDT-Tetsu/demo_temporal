package com.demo.onboarding.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * @param failureRate tuy chon, ghi de ONBOARDING_FAILURE_RATE cho rieng tenant nay (dung khi tao data test)
 */
public record CreateTenantRequest(
        @NotBlank String name,
        @NotBlank @Email String adminEmail,
        @NotBlank String plan,
        @DecimalMin("0.0") @DecimalMax("1.0") Double failureRate
) {
}

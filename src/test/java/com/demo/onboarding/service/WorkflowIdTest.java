package com.demo.onboarding.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowIdTest {

    @Test
    void containsSlugOfTenantName() {
        assertThat(TenantOnboardingService.workflowId("Acme Corp", "d270c483"))
                .isEqualTo("tenant-onboarding-acme-corp-d270c483");
    }

    @Test
    void removesVietnameseDiacritics() {
        assertThat(TenantOnboardingService.workflowId("Công ty Đại Việt", "a1"))
                .isEqualTo("tenant-onboarding-cong-ty-dai-viet-a1");
    }

    @Test
    void stripsSpecialCharactersAndTruncates() {
        assertThat(TenantOnboardingService.workflowId("  ACME & Co., Ltd!!  ", "a1"))
                .isEqualTo("tenant-onboarding-acme-co-ltd-a1");
        assertThat(TenantOnboardingService.workflowId("x".repeat(60), "a1"))
                .isEqualTo("tenant-onboarding-" + "x".repeat(40) + "-a1");
    }

    @Test
    void fallsBackToIdWhenNameHasNoUsableCharacters() {
        assertThat(TenantOnboardingService.workflowId("!!!", "a1"))
                .isEqualTo("tenant-onboarding-a1");
    }
}

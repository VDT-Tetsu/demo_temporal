package com.demo.onboarding.workflow;

import com.demo.onboarding.domain.OnboardingStep;
import com.demo.onboarding.domain.TenantStatus;

import java.util.List;

/** Ket qua tra ve cua query getState(), doc truc tiep tu workflow dang chay. */
public record WorkflowState(
        TenantStatus status,
        OnboardingStep currentStep,
        String message,
        List<OnboardingStep> completedSteps
) {
}

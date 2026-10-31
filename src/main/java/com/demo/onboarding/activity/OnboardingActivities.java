package com.demo.onboarding.activity;

import com.demo.onboarding.workflow.OnboardingInput;
import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface OnboardingActivities {

    void validateRequest(OnboardingInput input);

    void provisionDatabase(OnboardingInput input);

    void createAdminUser(OnboardingInput input);

    void setupBilling(OnboardingInput input);

    void activateTenant(OnboardingInput input);

    void sendWelcomeEmail(OnboardingInput input);

    // ----- Compensation (rollback) -----

    void deprovisionDatabase(OnboardingInput input);

    void deleteAdminUser(OnboardingInput input);

    void cancelBilling(OnboardingInput input);
}

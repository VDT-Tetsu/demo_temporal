package com.demo.onboarding.domain;

public enum OnboardingStep {
    VALIDATE_REQUEST,
    PROVISION_DATABASE,
    CREATE_ADMIN_USER,
    SETUP_BILLING,
    WAIT_APPROVAL,
    ACTIVATE_TENANT,
    SEND_WELCOME_EMAIL
}

package com.demo.onboarding.activity;

import com.demo.onboarding.config.OnboardingProperties;
import com.demo.onboarding.workflow.OnboardingInput;
import io.temporal.activity.Activity;
import io.temporal.failure.ApplicationFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Cac buoc onboarding chi ghi log + sleep de gia lap.
 * Moi buoc co the loi ngau nhien theo input.failureRate (mac dinh onboarding.failure-rate) de demo retry / rollback.
 */
@Component("onboardingActivities")
public class OnboardingActivitiesImpl implements OnboardingActivities {

    private static final Logger log = LoggerFactory.getLogger(OnboardingActivitiesImpl.class);

    private final OnboardingProperties properties;

    public OnboardingActivitiesImpl(OnboardingProperties properties) {
        this.properties = properties;
    }

    @Override
    public void validateRequest(OnboardingInput input) {
        simulate(input, "Validate onboarding request", true);
    }

    @Override
    public void provisionDatabase(OnboardingInput input) {
        simulate(input, "Provision database schema tenant_" + input.tenantId(), true);
    }

    @Override
    public void createAdminUser(OnboardingInput input) {
        simulate(input, "Create admin user " + input.adminEmail(), true);
    }

    @Override
    public void setupBilling(OnboardingInput input) {
        simulate(input, "Setup billing for plan " + input.plan(), true);
    }

    @Override
    public void activateTenant(OnboardingInput input) {
        simulate(input, "Activate tenant " + input.tenantName(), true);
    }

    @Override
    public void sendWelcomeEmail(OnboardingInput input) {
        simulate(input, "Send welcome email to " + input.adminEmail(), true);
    }

    @Override
    public void deprovisionDatabase(OnboardingInput input) {
        simulate(input, "[ROLLBACK] Drop database schema tenant_" + input.tenantId(), false);
    }

    @Override
    public void deleteAdminUser(OnboardingInput input) {
        simulate(input, "[ROLLBACK] Delete admin user " + input.adminEmail(), false);
    }

    @Override
    public void cancelBilling(OnboardingInput input) {
        simulate(input, "[ROLLBACK] Cancel billing for plan " + input.plan(), false);
    }

    private void simulate(OnboardingInput input, String action, boolean canFail) {
        int attempt = Activity.getExecutionContext().getInfo().getAttempt();
        long delayMs = ThreadLocalRandom.current().nextLong(
                properties.stepDelayMin().toMillis(), properties.stepDelayMax().toMillis() + 1);
        log.info("[tenant={}] {} (attempt {}, ~{}s)", input.tenantId(), action, attempt, delayMs / 1000);

        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw Activity.wrap(e);
        }

        if (canFail && ThreadLocalRandom.current().nextDouble() < input.failureRate()) {
            log.warn("[tenant={}] {} -> SIMULATED FAILURE (attempt {})", input.tenantId(), action, attempt);
            throw ApplicationFailure.newFailure("Simulated random failure: " + action, "SimulatedFailure");
        }
        log.info("[tenant={}] {} -> done", input.tenantId(), action);
    }
}

package com.demo.onboarding.workflow;

import com.demo.onboarding.activity.OnboardingActivities;
import com.demo.onboarding.activity.StatusUpdate;
import com.demo.onboarding.activity.TenantStatusActivities;
import com.demo.onboarding.domain.OnboardingStep;
import com.demo.onboarding.domain.TenantStatus;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.failure.TemporalFailure;
import io.temporal.workflow.Saga;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.demo.onboarding.domain.OnboardingStep.*;

public class TenantOnboardingWorkflowImpl implements TenantOnboardingWorkflow {

    private static final Logger log = Workflow.getLogger(TenantOnboardingWorkflowImpl.class);

    private enum ManualAction { RETRY, ABORT }

    // Cap nhat status vao DB khong duoc phep fail: retry mac dinh (vo han) cua Temporal
    private final TenantStatusActivities statusActivities = Workflow.newActivityStub(
            TenantStatusActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(10))
                    .build());

    private OnboardingInput input;
    private OnboardingActivities activities;
    private OnboardingActivities compensations;

    private TenantStatus status = TenantStatus.PENDING;
    private OnboardingStep currentStep;
    private String message;
    private final List<OnboardingStep> completedSteps = new ArrayList<>();

    private Boolean approved;
    private String decisionNote;
    private ManualAction manualAction;
    private String abortReason;

    @Override
    public void onboard(OnboardingInput input) {
        this.input = input;
        Duration activityTimeout = Duration.ofSeconds(input.activityTimeoutSeconds());
        this.activities = Workflow.newActivityStub(
                OnboardingActivities.class,
                ActivityOptions.newBuilder()
                        .setStartToCloseTimeout(activityTimeout)
                        .setRetryOptions(RetryOptions.newBuilder()
                                .setInitialInterval(Duration.ofSeconds(1))
                                .setBackoffCoefficient(2)
                                .setMaximumAttempts(input.activityMaxAttempts())
                                .build())
                        .build());
        this.compensations = Workflow.newActivityStub(
                OnboardingActivities.class,
                ActivityOptions.newBuilder()
                        .setStartToCloseTimeout(activityTimeout)
                        .build());

        Saga saga = new Saga(new Saga.Options.Builder().setContinueWithError(true).build());
        try {
            runStep(VALIDATE_REQUEST, () -> activities.validateRequest(input));

            runStep(PROVISION_DATABASE, () -> activities.provisionDatabase(input));
            saga.addCompensation(compensations::deprovisionDatabase, input);

            runStep(CREATE_ADMIN_USER, () -> activities.createAdminUser(input));
            saga.addCompensation(compensations::deleteAdminUser, input);

            runStep(SETUP_BILLING, () -> activities.setupBilling(input));
            saga.addCompensation(compensations::cancelBilling, input);

            waitForApproval();

            runStep(ACTIVATE_TENANT, () -> activities.activateTenant(input));
            runStep(SEND_WELCOME_EMAIL, () -> activities.sendWelcomeEmail(input));

            transition(TenantStatus.SUCCESS, currentStep, "Tenant onboarded successfully");
        } catch (TemporalFailure e) {
            String reason = rootMessage(e);
            log.warn("Onboarding tenant {} failed at {}: {}", input.tenantId(), currentStep, reason);
            transition(TenantStatus.COMPENSATING, currentStep, "Rolling back: " + reason);
            saga.compensate();
            transition(TenantStatus.FAILED, currentStep, reason);
            // Throw de workflow hien thi Failed tren Temporal UI
            throw ApplicationFailure.newNonRetryableFailure(reason, "OnboardingFailed");
        }
    }

    /**
     * Chay 1 buoc. Temporal tu retry theo RetryOptions; neu van loi thi workflow chuyen
     * sang WAITING_RETRY va dung lai cho den khi nhan signal retryStep / abort.
     */
    private void runStep(OnboardingStep step, Runnable action) {
        while (true) {
            transition(TenantStatus.IN_PROGRESS, step, "Running " + step);
            try {
                action.run();
                completedSteps.add(step);
                transition(TenantStatus.IN_PROGRESS, step, step + " completed");
                return;
            } catch (ActivityFailure e) {
                manualAction = null;
                transition(TenantStatus.WAITING_RETRY, step,
                        step + " failed after " + input.activityMaxAttempts() + " attempts: " + rootMessage(e)
                                + ". Waiting for retry/abort trigger");

                boolean triggered = Workflow.await(
                        Duration.ofSeconds(input.manualRetryTimeoutSeconds()), () -> manualAction != null);
                if (!triggered) {
                    throw ApplicationFailure.newNonRetryableFailure(
                            "No retry/abort trigger received for " + step, "ManualRetryTimeout");
                }
                if (manualAction == ManualAction.ABORT) {
                    throw ApplicationFailure.newNonRetryableFailure(
                            "Aborted at " + step + ": " + abortReason, "Aborted");
                }
                log.info("Manual retry triggered for tenant {} step {}", input.tenantId(), step);
            }
        }
    }

    private void waitForApproval() {
        transition(TenantStatus.WAITING_APPROVAL, WAIT_APPROVAL, "Waiting for admin approval");

        boolean decided = Workflow.await(
                Duration.ofSeconds(input.approvalTimeoutSeconds()), () -> approved != null);
        if (!decided) {
            throw ApplicationFailure.newNonRetryableFailure("Approval timed out", "ApprovalTimeout");
        }
        if (!approved) {
            throw ApplicationFailure.newNonRetryableFailure("Rejected: " + decisionNote, "Rejected");
        }
        completedSteps.add(WAIT_APPROVAL);
        transition(TenantStatus.IN_PROGRESS, WAIT_APPROVAL, "Approved by " + decisionNote);
    }

    private void transition(TenantStatus newStatus, OnboardingStep step, String newMessage) {
        this.status = newStatus;
        this.currentStep = step;
        this.message = newMessage;
        log.info("Tenant {} -> {} [{}] {}", input.tenantId(), newStatus, step, newMessage);
        statusActivities.updateStatus(new StatusUpdate(input.tenantId(), newStatus, step, newMessage));
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        if (root instanceof ApplicationFailure af) {
            return af.getOriginalMessage();
        }
        return root.getMessage();
    }

    @Override
    public void approve(String approvedBy) {
        if (status != TenantStatus.WAITING_APPROVAL) {
            log.warn("Ignore approve signal, current status is {}", status);
            return;
        }
        approved = true;
        decisionNote = approvedBy;
    }

    @Override
    public void reject(String reason) {
        if (status != TenantStatus.WAITING_APPROVAL) {
            log.warn("Ignore reject signal, current status is {}", status);
            return;
        }
        approved = false;
        decisionNote = reason;
    }

    @Override
    public void retryStep() {
        if (status != TenantStatus.WAITING_RETRY) {
            log.warn("Ignore retry signal, current status is {}", status);
            return;
        }
        manualAction = ManualAction.RETRY;
    }

    @Override
    public void abort(String reason) {
        if (status != TenantStatus.WAITING_RETRY) {
            log.warn("Ignore abort signal, current status is {}", status);
            return;
        }
        manualAction = ManualAction.ABORT;
        abortReason = reason;
    }

    @Override
    public WorkflowState getState() {
        return new WorkflowState(status, currentStep, message, List.copyOf(completedSteps));
    }
}

package com.demo.onboarding.workflow;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface TenantOnboardingWorkflow {

    @WorkflowMethod
    void onboard(OnboardingInput input);

    /** Trigger tiep tuc khi workflow dang WAITING_APPROVAL. */
    @SignalMethod
    void approve(String approvedBy);

    @SignalMethod
    void reject(String reason);

    /** Trigger chay lai buoc bi loi khi workflow dang WAITING_RETRY. */
    @SignalMethod
    void retryStep();

    /** Huy onboarding khi workflow dang WAITING_RETRY -> rollback + FAILED. */
    @SignalMethod
    void abort(String reason);

    @QueryMethod
    WorkflowState getState();
}

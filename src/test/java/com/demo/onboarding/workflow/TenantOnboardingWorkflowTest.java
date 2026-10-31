package com.demo.onboarding.workflow;

import com.demo.onboarding.activity.OnboardingActivities;
import com.demo.onboarding.activity.StatusUpdate;
import com.demo.onboarding.activity.TenantStatusActivities;
import com.demo.onboarding.domain.OnboardingStep;
import com.demo.onboarding.domain.TenantStatus;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.TestWorkflowExtension;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

class TenantOnboardingWorkflowTest {

    @RegisterExtension
    static final TestWorkflowExtension testWorkflow = TestWorkflowExtension.newBuilder()
            .setWorkflowTypes(TenantOnboardingWorkflowImpl.class)
            .setDoNotStart(true)
            .build();

    private OnboardingActivities activities;
    private String taskQueue;
    private final List<TenantStatus> statusHistory = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp(TestWorkflowEnvironment env, Worker worker) {
        activities = mock(OnboardingActivities.class, withSettings().withoutAnnotations());
        TenantStatusActivities statusActivities = (StatusUpdate update) -> statusHistory.add(update.status());
        worker.registerActivitiesImplementations(activities, statusActivities);
        taskQueue = worker.getTaskQueue();
        env.start();
    }

    @Test
    void approvedFlowEndsWithSuccess(WorkflowClient client) {
        TenantOnboardingWorkflow workflow = start(client);

        awaitStatus(workflow, TenantStatus.WAITING_APPROVAL);
        workflow.approve("tester");
        WorkflowStub.fromTyped(workflow).getResult(Void.class);

        assertThat(workflow.getState().status()).isEqualTo(TenantStatus.SUCCESS);
        assertThat(workflow.getState().completedSteps()).containsExactly(OnboardingStep.values());
        verify(activities, never()).deprovisionDatabase(any());
    }

    @Test
    void rejectedFlowRollsBackAndFails(WorkflowClient client) {
        TenantOnboardingWorkflow workflow = start(client);

        awaitStatus(workflow, TenantStatus.WAITING_APPROVAL);
        workflow.reject("bad data");

        assertThatThrownBy(() -> WorkflowStub.fromTyped(workflow).getResult(Void.class))
                .isInstanceOf(WorkflowFailedException.class);
        assertThat(workflow.getState().status()).isEqualTo(TenantStatus.FAILED);
        assertThat(workflow.getState().message()).isEqualTo("Rejected: bad data");
        verify(activities).cancelBilling(any());
        verify(activities).deleteAdminUser(any());
        verify(activities).deprovisionDatabase(any());
        verify(activities, never()).activateTenant(any());
    }

    @Test
    void approvalTimeoutFails(WorkflowClient client) {
        TenantOnboardingWorkflow workflow = start(client);

        // getResult cho phep test server skip thoi gian -> het han approval
        assertThatThrownBy(() -> WorkflowStub.fromTyped(workflow).getResult(Void.class))
                .isInstanceOf(WorkflowFailedException.class);
        assertThat(workflow.getState().message()).isEqualTo("Approval timed out");
    }

    @Test
    void failedStepWaitsForRetryTriggerThenContinues(WorkflowClient client) {
        doThrow(ApplicationFailure.newFailure("boom", "SimulatedFailure"))
                .doThrow(ApplicationFailure.newFailure("boom", "SimulatedFailure"))
                .doNothing()
                .when(activities).setupBilling(any());
        TenantOnboardingWorkflow workflow = start(client);

        awaitStatus(workflow, TenantStatus.WAITING_RETRY);
        assertThat(workflow.getState().currentStep()).isEqualTo(OnboardingStep.SETUP_BILLING);
        workflow.retryStep();

        awaitStatus(workflow, TenantStatus.WAITING_APPROVAL);
        workflow.approve("tester");
        WorkflowStub.fromTyped(workflow).getResult(Void.class);

        assertThat(workflow.getState().status()).isEqualTo(TenantStatus.SUCCESS);
        verify(activities, times(3)).setupBilling(any());
    }

    @Test
    void failedStepAbortRollsBackCompletedSteps(WorkflowClient client) {
        doThrow(ApplicationFailure.newFailure("boom", "SimulatedFailure"))
                .when(activities).setupBilling(any());
        TenantOnboardingWorkflow workflow = start(client);

        awaitStatus(workflow, TenantStatus.WAITING_RETRY);
        workflow.abort("give up");

        assertThatThrownBy(() -> WorkflowStub.fromTyped(workflow).getResult(Void.class))
                .isInstanceOf(WorkflowFailedException.class);
        assertThat(workflow.getState().status()).isEqualTo(TenantStatus.FAILED);
        assertThat(statusHistory).contains(TenantStatus.COMPENSATING);
        verify(activities).deleteAdminUser(any());
        verify(activities).deprovisionDatabase(any());
        verify(activities, never()).cancelBilling(any());
    }

    private TenantOnboardingWorkflow start(WorkflowClient client) {
        TenantOnboardingWorkflow workflow = client.newWorkflowStub(
                TenantOnboardingWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue(taskQueue).build());
        OnboardingInput input = new OnboardingInput("t1", "Acme", "admin@acme.io", "PRO", 0.0, 2, 30, 600, 600);
        WorkflowClient.start(workflow::onboard, input);
        return workflow;
    }

    private static void awaitStatus(TenantOnboardingWorkflow workflow, TenantStatus expected) {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (workflow.getState().status() != expected) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Timed out waiting for " + expected
                        + ", current " + workflow.getState().status());
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}

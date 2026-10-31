package com.demo.onboarding.service;

import com.demo.onboarding.api.CreateTenantRequest;
import com.demo.onboarding.api.TenantDetailView;
import com.demo.onboarding.api.TenantView;
import com.demo.onboarding.api.UiConfig;
import com.demo.onboarding.config.OnboardingProperties;
import com.demo.onboarding.domain.OnboardingEvent;
import com.demo.onboarding.domain.OnboardingEventRepository;
import com.demo.onboarding.domain.OnboardingStep;
import com.demo.onboarding.domain.Tenant;
import com.demo.onboarding.domain.TenantRepository;
import com.demo.onboarding.domain.TenantStatus;
import com.demo.onboarding.workflow.OnboardingInput;
import com.demo.onboarding.workflow.TenantOnboardingWorkflow;
import com.demo.onboarding.workflow.WorkflowState;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class TenantOnboardingService {

    private static final Logger log = LoggerFactory.getLogger(TenantOnboardingService.class);
    private static final String UNKNOWN = "UNKNOWN";
    private static final String NOT_FOUND = "NOT_FOUND";

    private final WorkflowClient workflowClient;
    private final TenantRepository tenantRepository;
    private final OnboardingEventRepository eventRepository;
    private final OnboardingProperties properties;

    public TenantOnboardingService(WorkflowClient workflowClient,
                                   TenantRepository tenantRepository,
                                   OnboardingEventRepository eventRepository,
                                   OnboardingProperties properties) {
        this.workflowClient = workflowClient;
        this.tenantRepository = tenantRepository;
        this.eventRepository = eventRepository;
        this.properties = properties;
    }

    public Tenant startOnboarding(CreateTenantRequest request) {
        String tenantId = UUID.randomUUID().toString().substring(0, 8);
        String workflowId = workflowId(request.name(), tenantId);
        Tenant tenant = tenantRepository.save(
                new Tenant(tenantId, request.name(), request.adminEmail(), request.plan(), workflowId));
        eventRepository.save(new OnboardingEvent(tenantId, TenantStatus.PENDING, null, "Tenant created"));

        OnboardingInput input = new OnboardingInput(
                tenantId,
                request.name(),
                request.adminEmail(),
                request.plan(),
                request.failureRate() != null ? request.failureRate() : properties.failureRate(),
                properties.activityMaxAttempts(),
                // Moi buoc sleep toi da stepDelayMax, cong them 30s de activity khong bi timeout
                properties.stepDelayMax().plusSeconds(30).toSeconds(),
                properties.approvalTimeout().toSeconds(),
                properties.manualRetryTimeout().toSeconds());

        TenantOnboardingWorkflow workflow = workflowClient.newWorkflowStub(
                TenantOnboardingWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId(workflowId)
                        .setTaskQueue(properties.taskQueue())
                        .build());
        WorkflowClient.start(workflow::onboard, input);
        log.info("Started workflow {} for tenant {}", workflowId, tenantId);
        return tenant;
    }

    public List<Tenant> listTenants() {
        return tenantRepository.findAllByOrderByCreatedAtDesc();
    }

    public Tenant getTenant(String tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new NoSuchElementException("Tenant not found: " + tenantId));
    }

    public List<OnboardingEvent> getEvents(String tenantId) {
        getTenant(tenantId);
        return eventRepository.findByTenantIdOrderByIdAsc(tenantId);
    }

    public WorkflowState queryWorkflowState(String tenantId) {
        return workflowStub(tenantId).getState();
    }

    /** Danh sach tenant kem execution status tren Temporal (1 lan goi visibility cho tat ca). */
    public List<TenantView> overview() {
        Map<String, String> temporalStatuses = temporalStatuses("WorkflowType = 'TenantOnboardingWorkflow'");
        return listTenants().stream()
                .map(t -> TenantView.of(t, temporalStatusOf(temporalStatuses, t.getWorkflowId())))
                .toList();
    }

    public TenantDetailView detail(String tenantId) {
        Tenant tenant = getTenant(tenantId);
        List<OnboardingEvent> events = eventRepository.findByTenantIdOrderByIdAsc(tenantId);
        String temporalStatus = temporalStatusOf(
                temporalStatuses("WorkflowId = '" + tenant.getWorkflowId() + "'"), tenant.getWorkflowId());

        WorkflowState state = null;
        String queryError = null;
        try {
            state = workflowStub(tenantId).getState();
        } catch (RuntimeException e) {
            queryError = e.getMessage();
        }

        Instant waitingSince = null;
        Instant waitingDeadline = null;
        Duration timeout = switch (tenant.getStatus()) {
            case WAITING_APPROVAL -> properties.approvalTimeout();
            case WAITING_RETRY -> properties.manualRetryTimeout();
            default -> null;
        };
        if (timeout != null) {
            for (OnboardingEvent event : events) {
                if (event.getStatus() == tenant.getStatus()) {
                    waitingSince = event.getCreatedAt();
                }
            }
            waitingDeadline = waitingSince == null ? null : waitingSince.plus(timeout);
        }

        return new TenantDetailView(TenantView.of(tenant, temporalStatus), state, queryError, events,
                waitingSince, waitingDeadline);
    }

    public UiConfig uiConfig() {
        return new UiConfig(
                properties.temporalUiUrl(),
                properties.temporalNamespace(),
                List.of(OnboardingStep.values()),
                properties.failureRate(),
                properties.stepDelayMin().toSeconds(),
                properties.stepDelayMax().toSeconds(),
                properties.approvalTimeout().toSeconds(),
                properties.manualRetryTimeout().toSeconds());
    }

    /** null neu khong goi duoc Temporal. */
    private Map<String, String> temporalStatuses(String query) {
        Map<String, String> result = new HashMap<>();
        try {
            // Visibility tra ve run moi nhat truoc, nen giu gia tri dau tien cua moi workflowId
            workflowClient.listExecutions(query).forEach(meta -> result.putIfAbsent(
                    meta.getExecution().getWorkflowId(),
                    meta.getStatus().name().replace("WORKFLOW_EXECUTION_STATUS_", "")));
            return result;
        } catch (RuntimeException e) {
            log.warn("Cannot list workflow executions from Temporal: {}", e.getMessage());
            return null;
        }
    }

    /** NOT_FOUND: Temporal da xoa workflow (het retention cua namespace); UNKNOWN: khong goi duoc Temporal. */
    private static String temporalStatusOf(Map<String, String> statuses, String workflowId) {
        if (statuses == null) {
            return UNKNOWN;
        }
        return statuses.getOrDefault(workflowId, NOT_FOUND);
    }

    public void approve(String tenantId, String approvedBy) {
        signal(tenantId, TenantStatus.WAITING_APPROVAL, wf -> wf.approve(approvedBy));
    }

    public void reject(String tenantId, String reason) {
        signal(tenantId, TenantStatus.WAITING_APPROVAL, wf -> wf.reject(reason));
    }

    public void retry(String tenantId) {
        signal(tenantId, TenantStatus.WAITING_RETRY, TenantOnboardingWorkflow::retryStep);
    }

    public void abort(String tenantId, String reason) {
        signal(tenantId, TenantStatus.WAITING_RETRY, wf -> wf.abort(reason));
    }

    private void signal(String tenantId, TenantStatus expected, Consumer<TenantOnboardingWorkflow> action) {
        TenantOnboardingWorkflow workflow = workflowStub(tenantId);
        TenantStatus current = workflow.getState().status();
        if (current != expected) {
            throw new IllegalStateException(
                    "Tenant " + tenantId + " is " + current + ", expected " + expected);
        }
        action.accept(workflow);
    }

    /** VD: "Công ty Đại Việt" + "d270c483" -> "tenant-onboarding-cong-ty-dai-viet-d270c483". */
    static String workflowId(String tenantName, String tenantId) {
        String slug = Normalizer.normalize(tenantName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.length() > 40) {
            slug = slug.substring(0, 40).replaceAll("-+$", "");
        }
        return slug.isEmpty()
                ? "tenant-onboarding-" + tenantId
                : "tenant-onboarding-" + slug + "-" + tenantId;
    }

    private TenantOnboardingWorkflow workflowStub(String tenantId) {
        return workflowClient.newWorkflowStub(TenantOnboardingWorkflow.class, getTenant(tenantId).getWorkflowId());
    }
}

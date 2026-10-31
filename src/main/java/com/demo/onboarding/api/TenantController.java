package com.demo.onboarding.api;

import com.demo.onboarding.domain.OnboardingEvent;
import com.demo.onboarding.domain.Tenant;
import com.demo.onboarding.service.TenantOnboardingService;
import com.demo.onboarding.workflow.WorkflowState;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantOnboardingService service;

    public TenantController(TenantOnboardingService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Tenant create(@Valid @RequestBody CreateTenantRequest request) {
        return service.startOnboarding(request);
    }

    @GetMapping
    public List<Tenant> list() {
        return service.listTenants();
    }

    /** Danh sach tenant kem execution status cua Temporal (dung cho FE). */
    @GetMapping("/overview")
    public List<TenantView> overview() {
        return service.overview();
    }

    @GetMapping("/{id}")
    public Tenant get(@PathVariable String id) {
        return service.getTenant(id);
    }

    /** Tenant + workflow query + timeline + han cho trigger (dung cho FE). */
    @GetMapping("/{id}/detail")
    public TenantDetailView detail(@PathVariable String id) {
        return service.detail(id);
    }

    @GetMapping("/{id}/events")
    public List<OnboardingEvent> events(@PathVariable String id) {
        return service.getEvents(id);
    }

    @GetMapping("/{id}/workflow-state")
    public WorkflowState workflowState(@PathVariable String id) {
        return service.queryWorkflowState(id);
    }

    @PostMapping("/{id}/approve")
    public Map<String, String> approve(@PathVariable String id,
                                       @RequestBody(required = false) DecisionRequest body) {
        service.approve(id, valueOr(body == null ? null : body.actor(), "admin"));
        return Map.of("result", "approve signal sent");
    }

    @PostMapping("/{id}/reject")
    public Map<String, String> reject(@PathVariable String id,
                                      @RequestBody(required = false) DecisionRequest body) {
        service.reject(id, valueOr(body == null ? null : body.reason(), "no reason"));
        return Map.of("result", "reject signal sent");
    }

    @PostMapping("/{id}/retry")
    public Map<String, String> retry(@PathVariable String id) {
        service.retry(id);
        return Map.of("result", "retry signal sent");
    }

    @PostMapping("/{id}/abort")
    public Map<String, String> abort(@PathVariable String id,
                                     @RequestBody(required = false) DecisionRequest body) {
        service.abort(id, valueOr(body == null ? null : body.reason(), "aborted by operator"));
        return Map.of("result", "abort signal sent");
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

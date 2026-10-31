package com.demo.onboarding.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "tenants")
public class Tenant {

    @Id
    private String id;

    private String name;

    private String adminEmail;

    private String plan;

    @Enumerated(EnumType.STRING)
    private TenantStatus status;

    @Enumerated(EnumType.STRING)
    private OnboardingStep currentStep;

    @Column(length = 2000)
    private String message;

    private String workflowId;

    private Instant createdAt;

    private Instant updatedAt;

    protected Tenant() {
    }

    public Tenant(String id, String name, String adminEmail, String plan, String workflowId) {
        this.id = id;
        this.name = name;
        this.adminEmail = adminEmail;
        this.plan = plan;
        this.workflowId = workflowId;
        this.status = TenantStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void updateStatus(TenantStatus status, OnboardingStep step, String message) {
        this.status = status;
        this.currentStep = step;
        this.message = message;
        this.updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAdminEmail() {
        return adminEmail;
    }

    public String getPlan() {
        return plan;
    }

    public TenantStatus getStatus() {
        return status;
    }

    public OnboardingStep getCurrentStep() {
        return currentStep;
    }

    public String getMessage() {
        return message;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

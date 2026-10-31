package com.demo.onboarding.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Lich su chuyen trang thai cua tenant, dung de xem timeline onboarding. */
@Entity
@Table(name = "onboarding_events")
public class OnboardingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String tenantId;

    @Enumerated(EnumType.STRING)
    private TenantStatus status;

    @Enumerated(EnumType.STRING)
    private OnboardingStep step;

    @Column(length = 2000)
    private String message;

    private Instant createdAt;

    protected OnboardingEvent() {
    }

    public OnboardingEvent(String tenantId, TenantStatus status, OnboardingStep step, String message) {
        this.tenantId = tenantId;
        this.status = status;
        this.step = step;
        this.message = message;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public TenantStatus getStatus() {
        return status;
    }

    public OnboardingStep getStep() {
        return step;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

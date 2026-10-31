package com.demo.onboarding.service;

import com.demo.onboarding.activity.StatusUpdate;
import com.demo.onboarding.domain.OnboardingEvent;
import com.demo.onboarding.domain.OnboardingEventRepository;
import com.demo.onboarding.domain.Tenant;
import com.demo.onboarding.domain.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class TenantStatusService {

    private final TenantRepository tenantRepository;
    private final OnboardingEventRepository eventRepository;

    public TenantStatusService(TenantRepository tenantRepository, OnboardingEventRepository eventRepository) {
        this.tenantRepository = tenantRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public void updateStatus(StatusUpdate update) {
        Tenant tenant = tenantRepository.findById(update.tenantId())
                .orElseThrow(() -> new NoSuchElementException("Tenant not found: " + update.tenantId()));
        tenant.updateStatus(update.status(), update.step(), update.message());
        eventRepository.save(new OnboardingEvent(update.tenantId(), update.status(), update.step(), update.message()));
    }
}

package com.demo.onboarding.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OnboardingEventRepository extends JpaRepository<OnboardingEvent, Long> {

    List<OnboardingEvent> findByTenantIdOrderByIdAsc(String tenantId);
}

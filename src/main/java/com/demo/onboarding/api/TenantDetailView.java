package com.demo.onboarding.api;

import com.demo.onboarding.domain.OnboardingEvent;
import com.demo.onboarding.workflow.WorkflowState;

import java.time.Instant;
import java.util.List;

/**
 * Du lieu cho man hinh chi tiet tren FE.
 *
 * @param workflowState    ket qua query getState() cua workflow, null neu query loi (vd worker khong chay)
 * @param waitingDeadline  thoi diem het han cho trigger (tinh theo cau hinh timeout hien tai), null neu khong cho
 */
public record TenantDetailView(
        TenantView tenant,
        WorkflowState workflowState,
        String queryError,
        List<OnboardingEvent> events,
        Instant waitingSince,
        Instant waitingDeadline
) {
}

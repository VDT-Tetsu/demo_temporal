package com.demo.onboarding.activity;

import com.demo.onboarding.service.TenantStatusService;
import org.springframework.stereotype.Component;

/** Delegate sang service de giu activity bean khong bi proxy boi @Transactional. */
@Component("tenantStatusActivities")
public class TenantStatusActivitiesImpl implements TenantStatusActivities {

    private final TenantStatusService tenantStatusService;

    public TenantStatusActivitiesImpl(TenantStatusService tenantStatusService) {
        this.tenantStatusService = tenantStatusService;
    }

    @Override
    public void updateStatus(StatusUpdate update) {
        tenantStatusService.updateStatus(update);
    }
}

package com.demo.onboarding.activity;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface TenantStatusActivities {

    void updateStatus(StatusUpdate update);
}

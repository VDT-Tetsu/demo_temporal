package com.demo.onboarding.domain;

public enum TenantStatus {
    /** Da tao tenant, workflow chua chay buoc nao. */
    PENDING,
    /** Dang chay 1 buoc onboarding. */
    IN_PROGRESS,
    /** Dang cho admin approve/reject (signal). */
    WAITING_APPROVAL,
    /** 1 buoc loi het so lan auto-retry, dang cho trigger retry/abort (signal). */
    WAITING_RETRY,
    /** Dang rollback cac buoc da thanh cong. */
    COMPENSATING,
    SUCCESS,
    FAILED
}

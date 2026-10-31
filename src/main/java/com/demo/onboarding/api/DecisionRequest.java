package com.demo.onboarding.api;

/** Body tuy chon cho cac API approve / reject / abort. */
public record DecisionRequest(String actor, String reason) {
}

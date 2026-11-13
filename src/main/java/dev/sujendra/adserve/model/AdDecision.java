package dev.sujendra.adserve.model;

/** The winning ad and the price the advertiser pays (second-price, in cents). */
public record AdDecision(String requestId, String campaignId, String creative, long priceCents) {}

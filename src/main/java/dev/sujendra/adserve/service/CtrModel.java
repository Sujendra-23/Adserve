package dev.sujendra.adserve.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;

/**
 * Smoothed click-through rate per campaign, learned from aggregate events.
 * ctr = (clicks + 1) / (impressions + 20): a Beta-style prior so a brand-new campaign
 * starts at 5% instead of 0% or 100%.
 */
@Component
public class CtrModel {
    private static final double PRIOR_CLICKS = 1.0;
    private static final double PRIOR_IMPRESSIONS = 20.0;

    private final Map<String, LongAdder> impressions = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> clicks = new ConcurrentHashMap<>();

    public void recordImpression(String campaignId) {
        impressions.computeIfAbsent(campaignId, k -> new LongAdder()).increment();
    }

    public void recordClick(String campaignId) {
        clicks.computeIfAbsent(campaignId, k -> new LongAdder()).increment();
    }

    public double ctr(String campaignId) {
        double imp = impressions.getOrDefault(campaignId, new LongAdder()).sum();
        double clk = clicks.getOrDefault(campaignId, new LongAdder()).sum();
        return (clk + PRIOR_CLICKS) / (imp + PRIOR_IMPRESSIONS);
    }

    public long impressions(String campaignId) {
        LongAdder a = impressions.get(campaignId);
        return a == null ? 0 : a.sum();
    }

    public long clicks(String campaignId) {
        LongAdder a = clicks.get(campaignId);
        return a == null ? 0 : a.sum();
    }
}

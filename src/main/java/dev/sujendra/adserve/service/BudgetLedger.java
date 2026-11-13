package dev.sujendra.adserve.service;

import dev.sujendra.adserve.model.Campaign;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Lock-free per-campaign budget. {@link #tryReserve} uses a compare-and-set loop so that, however many
 * threads bid at once, total reserved spend can never exceed the daily budget.
 */
@Component
public class BudgetLedger {
    private final Map<String, AtomicLong> remaining = new ConcurrentHashMap<>();

    public void register(Campaign c) {
        remaining.put(c.id(), new AtomicLong(c.dailyBudgetCents()));
    }

    public boolean tryReserve(String campaignId, long cents) {
        AtomicLong left = remaining.get(campaignId);
        if (left == null || cents < 0) return false;
        while (true) {
            long cur = left.get();
            if (cur < cents) return false;
            if (left.compareAndSet(cur, cur - cents)) return true;
        }
    }

    public long remaining(String campaignId) {
        AtomicLong left = remaining.get(campaignId);
        return left == null ? 0 : left.get();
    }
}

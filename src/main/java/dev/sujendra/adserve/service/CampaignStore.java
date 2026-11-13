package dev.sujendra.adserve.service;

import dev.sujendra.adserve.model.AdRequest;
import dev.sujendra.adserve.model.Campaign;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** In-memory campaign index keyed by placement, so a request only scans campaigns that can match it. */
@Component
public class CampaignStore {
    private final Map<String, Campaign> byId = new ConcurrentHashMap<>();
    private final Map<String, List<Campaign>> byPlacement = new ConcurrentHashMap<>();
    private final List<Campaign> wildcard = new ArrayList<>();
    private final BudgetLedger ledger;

    public CampaignStore(BudgetLedger ledger) {
        this.ledger = ledger;
    }

    public synchronized void put(Campaign c) {
        byId.put(c.id(), c);
        ledger.register(c);
        if (c.placements().isEmpty()) {
            wildcard.add(c);
        } else {
            for (String p : c.placements()) {
                byPlacement.computeIfAbsent(p, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(c);
            }
        }
    }

    public Optional<Campaign> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Collection<Campaign> all() {
        return byId.values();
    }

    /** Campaigns whose targeting matches the request (before budget checks). */
    public List<Campaign> candidates(AdRequest r) {
        List<Campaign> out = new ArrayList<>();
        for (Campaign c : byPlacement.getOrDefault(r.placement(), List.of())) {
            if (c.matches(r)) out.add(c);
        }
        synchronized (this) {
            for (Campaign c : wildcard) {
                if (c.matches(r)) out.add(c);
            }
        }
        return out;
    }
}

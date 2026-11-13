package dev.sujendra.adserve.web;

import dev.sujendra.adserve.events.EventPublisher;
import dev.sujendra.adserve.model.AdDecision;
import dev.sujendra.adserve.model.AdEvent;
import dev.sujendra.adserve.model.AdRequest;
import dev.sujendra.adserve.service.AuctionService;
import dev.sujendra.adserve.service.BudgetLedger;
import dev.sujendra.adserve.service.CampaignStore;
import dev.sujendra.adserve.service.CtrModel;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class AdController {
    private final AuctionService auction;
    private final EventPublisher publisher;
    private final CampaignStore store;
    private final BudgetLedger ledger;
    private final CtrModel ctr;

    public AdController(AuctionService auction, EventPublisher publisher, CampaignStore store,
                        BudgetLedger ledger, CtrModel ctr) {
        this.auction = auction;
        this.publisher = publisher;
        this.store = store;
        this.ledger = ledger;
        this.ctr = ctr;
    }

    /** 200 with the winning ad, or 204 when nothing eligible has budget. */
    @PostMapping("/ads/decide")
    public ResponseEntity<AdDecision> decide(@Valid @RequestBody AdRequest request) {
        return auction.decide(request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/events")
    public ResponseEntity<Void> event(@Valid @RequestBody AdEvent event) {
        if (store.find(event.campaignId()).isEmpty()) return ResponseEntity.notFound().build();
        publisher.publish(event);
        return ResponseEntity.accepted().build();
    }

    /** Aggregate-only report for one campaign. */
    @GetMapping("/campaigns/{id}/report")
    public ResponseEntity<Map<String, Object>> report(@PathVariable String id) {
        return store.find(id)
                .map(c -> ResponseEntity.ok(Map.<String, Object>of(
                        "campaignId", c.id(),
                        "impressions", ctr.impressions(id),
                        "clicks", ctr.clicks(id),
                        "ctr", ctr.ctr(id),
                        "budgetRemainingCents", ledger.remaining(id))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

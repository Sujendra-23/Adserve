package dev.sujendra.adserve.service;

import dev.sujendra.adserve.model.AdDecision;
import dev.sujendra.adserve.model.AdRequest;
import dev.sujendra.adserve.model.Campaign;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Second-price auction on expected revenue per impression (bid x predicted CTR).
 * The winner pays the smallest price that still beats the runner-up, capped at its own bid.
 * Spend is reserved atomically; if the winner has no budget left, the next bidder is tried.
 */
@Service
public class AuctionService {
    static final long RESERVE_PRICE_CENTS = 1;

    private final CampaignStore store;
    private final CtrModel ctrModel;
    private final BudgetLedger ledger;
    private final Timer decisionTimer;

    public AuctionService(CampaignStore store, CtrModel ctrModel, BudgetLedger ledger, MeterRegistry registry) {
        this.store = store;
        this.ctrModel = ctrModel;
        this.ledger = ledger;
        this.decisionTimer = Timer.builder("adserve.decision.latency")
                .description("Time to run one ad auction")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    public Optional<AdDecision> decide(AdRequest request) {
        return decisionTimer.record(() -> runAuction(request));
    }

    private record Bid(Campaign campaign, double ctr, double score) {}

    private Optional<AdDecision> runAuction(AdRequest request) {
        List<Bid> bids = new ArrayList<>();
        for (Campaign c : store.candidates(request)) {
            if (ledger.remaining(c.id()) < RESERVE_PRICE_CENTS) continue;
            double ctr = ctrModel.ctr(c.id());
            bids.add(new Bid(c, ctr, c.bidCents() * ctr));
        }
        bids.sort(Comparator.comparingDouble(Bid::score).reversed()
                .thenComparing(b -> b.campaign().id()));

        for (int i = 0; i < bids.size(); i++) {
            Bid winner = bids.get(i);
            double nextScore = i + 1 < bids.size() ? bids.get(i + 1).score() : 0.0;
            long price = (long) Math.ceil(nextScore / winner.ctr());
            price = Math.max(RESERVE_PRICE_CENTS, Math.min(price, winner.campaign().bidCents()));
            if (ledger.tryReserve(winner.campaign().id(), price)) {
                return Optional.of(new AdDecision(
                        UUID.randomUUID().toString(), winner.campaign().id(), winner.campaign().creative(), price));
            }
        }
        return Optional.empty();
    }
}

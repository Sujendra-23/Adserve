package dev.sujendra.adserve;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sujendra.adserve.model.AdDecision;
import dev.sujendra.adserve.model.AdRequest;
import dev.sujendra.adserve.model.Campaign;
import dev.sujendra.adserve.service.AuctionService;
import dev.sujendra.adserve.service.BudgetLedger;
import dev.sujendra.adserve.service.CampaignStore;
import dev.sujendra.adserve.service.CtrModel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuctionServiceTest {
    BudgetLedger ledger;
    CampaignStore store;
    CtrModel ctr;
    AuctionService auction;

    @BeforeEach
    void setUp() {
        ledger = new BudgetLedger();
        store = new CampaignStore(ledger);
        ctr = new CtrModel();
        auction = new AuctionService(store, ctr, ledger, new SimpleMeterRegistry());
    }

    private Campaign campaign(String id, long bid, long budget, String placement) {
        return new Campaign(id, "adv-" + id, "creative-" + id, bid, budget, Set.of(placement), Set.of(), Set.of());
    }

    @Test
    void highestBidWinsWhenCtrIsEqual() {
        store.put(campaign("low", 20, 1000, "search"));
        store.put(campaign("high", 50, 1000, "search"));
        Optional<AdDecision> d = auction.decide(new AdRequest("search", null, null));
        assertThat(d).isPresent();
        assertThat(d.get().campaignId()).isEqualTo("high");
    }

    @Test
    void winnerPaysSecondPriceNotItsOwnBid() {
        store.put(campaign("low", 20, 1000, "search"));
        store.put(campaign("high", 50, 1000, "search"));
        AdDecision d = auction.decide(new AdRequest("search", null, null)).orElseThrow();
        assertThat(d.priceCents()).isEqualTo(20);
        assertThat(ledger.remaining("high")).isEqualTo(980);
    }

    @Test
    void betterPredictedClickRateCanBeatAHigherBid() {
        store.put(campaign("cheapButClicked", 30, 1000, "search"));
        store.put(campaign("pricierIgnored", 40, 1000, "search"));
        for (int i = 0; i < 200; i++) ctr.recordImpression("cheapButClicked");
        for (int i = 0; i < 100; i++) ctr.recordClick("cheapButClicked");
        for (int i = 0; i < 200; i++) ctr.recordImpression("pricierIgnored");
        AdDecision d = auction.decide(new AdRequest("search", null, null)).orElseThrow();
        assertThat(d.campaignId()).isEqualTo("cheapButClicked");
        assertThat(d.priceCents()).isLessThanOrEqualTo(30);
    }

    @Test
    void onlyCampaignsTargetingThePlacementCompete() {
        store.put(campaign("news-only", 90, 1000, "news"));
        store.put(campaign("search-ad", 10, 1000, "search"));
        AdDecision d = auction.decide(new AdRequest("search", null, null)).orElseThrow();
        assertThat(d.campaignId()).isEqualTo("search-ad");
    }

    @Test
    void localeAndCategoryTargetingFilters() {
        store.put(new Campaign("ca", "a", "x", 10, 100, Set.of("search"), Set.of("en-US"), Set.of("games")));
        assertThat(auction.decide(new AdRequest("search", "fr-FR", "games"))).isEmpty();
        assertThat(auction.decide(new AdRequest("search", "en-US", "health"))).isEmpty();
        assertThat(auction.decide(new AdRequest("search", "en-US", "games"))).isPresent();
    }

    @Test
    void exhaustedBudgetFallsThroughToNextBidder() {
        store.put(campaign("rich-bid-no-budget", 90, 0, "search"));
        store.put(campaign("backup", 10, 1000, "search"));
        AdDecision d = auction.decide(new AdRequest("search", null, null)).orElseThrow();
        assertThat(d.campaignId()).isEqualTo("backup");
    }

    @Test
    void noEligibleCampaignMeansNoAd() {
        assertThat(auction.decide(new AdRequest("search", null, null))).isEmpty();
    }

    @Test
    void campaignStopsServingOnceBudgetIsSpent() {
        store.put(campaign("only", 10, 3, "search"));
        int served = 0;
        for (int i = 0; i < 10; i++) {
            if (auction.decide(new AdRequest("search", null, null)).isPresent()) served++;
        }
        assertThat(served).isEqualTo(3);
        assertThat(ledger.remaining("only")).isZero();
    }
}

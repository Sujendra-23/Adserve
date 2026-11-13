package dev.sujendra.adserve.web;

import dev.sujendra.adserve.model.Campaign;
import dev.sujendra.adserve.service.CampaignStore;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Loads a few demo campaigns so the service answers requests out of the box. Off in tests. */
@Component
@ConditionalOnProperty(name = "adserve.sample-data", havingValue = "true", matchIfMissing = true)
public class SampleData implements ApplicationRunner {
    private final CampaignStore store;

    public SampleData(CampaignStore store) {
        this.store = store;
    }

    @Override
    public void run(ApplicationArguments args) {
        store.put(new Campaign("c-games", "Pixel Games", "Play free today", 50, 10_000,
                Set.of("app-store-search"), Set.of("en-US"), Set.of("games")));
        store.put(new Campaign("c-fitness", "FitTrack", "Track every workout", 40, 10_000,
                Set.of("app-store-search", "news-feed"), Set.of(), Set.of("health")));
        store.put(new Campaign("c-news", "Daily Brief", "Start with the news", 30, 5_000,
                Set.of("news-feed"), Set.of("en-US", "en-GB"), Set.of()));
    }
}

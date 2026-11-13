package dev.sujendra.adserve;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sujendra.adserve.model.AdRequest;
import dev.sujendra.adserve.model.Campaign;
import dev.sujendra.adserve.service.AuctionService;
import dev.sujendra.adserve.service.BudgetLedger;
import dev.sujendra.adserve.service.CampaignStore;
import dev.sujendra.adserve.service.CtrModel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * In-process timing of the auction alone (no HTTP, no network): 500 campaigns, 8 threads.
 * It prints p50/p99 so the numbers can be quoted from a real run; the assertion is deliberately loose.
 */
class LatencyBenchmarkTest {

    @Test
    void auctionLatencyWith500Campaigns() throws Exception {
        BudgetLedger ledger = new BudgetLedger();
        CampaignStore store = new CampaignStore(ledger);
        CtrModel ctr = new CtrModel();
        AuctionService auction = new AuctionService(store, ctr, ledger, new SimpleMeterRegistry());
        for (int i = 0; i < 500; i++) {
            store.put(new Campaign("c" + i, "adv", "x", 10 + (i % 90), Long.MAX_VALUE / 4,
                    Set.of("search"), Set.of(), Set.of()));
        }
        AdRequest request = new AdRequest("search", "en-US", null);
        for (int i = 0; i < 20_000; i++) auction.decide(request); // warm up the JIT

        int threads = 8, perThread = 25_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<long[]>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            Callable<long[]> job = () -> {
                long[] nanos = new long[perThread];
                for (int i = 0; i < perThread; i++) {
                    long s = System.nanoTime();
                    auction.decide(request);
                    nanos[i] = System.nanoTime() - s;
                }
                return nanos;
            };
            futures.add(pool.submit(job));
        }
        long[] all = new long[threads * perThread];
        int pos = 0;
        long wallStart = System.nanoTime();
        for (Future<long[]> f : futures) {
            long[] a = f.get();
            System.arraycopy(a, 0, all, pos, a.length);
            pos += a.length;
        }
        pool.shutdown();
        Arrays.sort(all);
        double p50 = all[all.length / 2] / 1000.0;
        double p99 = all[(int) (all.length * 0.99)] / 1000.0;
        System.out.printf("AUCTION_LATENCY decisions=%d campaigns=500 threads=%d p50=%.1fus p99=%.1fus%n",
                all.length, threads, p50, p99);
        assertThat(p99).isLessThan(50_000.0); // 50 ms: loose on purpose so slow CI machines do not flake
    }
}

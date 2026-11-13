package dev.sujendra.adserve;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sujendra.adserve.model.Campaign;
import dev.sujendra.adserve.service.BudgetLedger;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BudgetLedgerConcurrencyTest {

    @Test
    void neverOverspendsUnderContention() throws Exception {
        BudgetLedger ledger = new BudgetLedger();
        ledger.register(new Campaign("c1", "a", "x", 5, 1_000, Set.of(), Set.of(), Set.of()));
        int threads = 64, attemptsPerThread = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger granted = new AtomicInteger();
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                start.await();
                for (int i = 0; i < attemptsPerThread; i++) {
                    if (ledger.tryReserve("c1", 1)) granted.incrementAndGet();
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        // 6,400 attempts against a 1,000-cent budget: exactly 1,000 succeed.
        assertThat(granted.get()).isEqualTo(1_000);
        assertThat(ledger.remaining("c1")).isZero();
    }

    @Test
    void unknownCampaignAndNegativeAmountsAreRejected() {
        BudgetLedger ledger = new BudgetLedger();
        assertThat(ledger.tryReserve("missing", 1)).isFalse();
        ledger.register(new Campaign("c1", "a", "x", 5, 10, Set.of(), Set.of(), Set.of()));
        assertThat(ledger.tryReserve("c1", -1)).isFalse();
        assertThat(ledger.remaining("c1")).isEqualTo(10);
    }
}

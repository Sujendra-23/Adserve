package dev.sujendra.adserve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.sujendra.adserve.events.EventPublisher;
import dev.sujendra.adserve.model.AdEvent;
import dev.sujendra.adserve.model.Campaign;
import dev.sujendra.adserve.service.CampaignStore;
import dev.sujendra.adserve.service.CtrModel;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;

/** Runs a real (in-JVM) Kafka broker: events published through the producer must reach the consumer and move the CTR. */
@SpringBootTest(properties = {
        "adserve.sample-data=false",
        "adserve.kafka.enabled=true",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest"})
@EmbeddedKafka(partitions = 1, topics = "ad-events")
class KafkaEventFlowTest {
    @Autowired EventPublisher publisher;
    @Autowired CampaignStore store;
    @Autowired CtrModel ctr;

    @Test
    void eventsFlowThroughKafkaIntoTheCtrModel() {
        store.put(new Campaign("k-1", "Acme", "x", 10, 100, Set.of("p"), Set.of(), Set.of()));
        double before = ctr.ctr("k-1");
        for (int i = 0; i < 20; i++) publisher.publish(new AdEvent("k-1", "p", AdEvent.Type.IMPRESSION));
        for (int i = 0; i < 10; i++) publisher.publish(new AdEvent("k-1", "p", AdEvent.Type.CLICK));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(ctr.impressions("k-1")).isEqualTo(20);
            assertThat(ctr.clicks("k-1")).isEqualTo(10);
        });
        // (10 + 1) / (20 + 20) = 0.275, well above the 5% prior
        assertThat(ctr.ctr("k-1")).isGreaterThan(before).isEqualTo(0.275);
    }
}

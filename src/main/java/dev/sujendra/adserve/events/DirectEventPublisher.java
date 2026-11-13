package dev.sujendra.adserve.events;

import dev.sujendra.adserve.model.AdEvent;
import dev.sujendra.adserve.service.EventProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Used when Kafka is switched off (the default): events update the CTR model in-process. */
@Component
@ConditionalOnProperty(name = "adserve.kafka.enabled", havingValue = "false", matchIfMissing = true)
public class DirectEventPublisher implements EventPublisher {
    private final EventProcessor processor;

    public DirectEventPublisher(EventProcessor processor) {
        this.processor = processor;
    }

    @Override
    public void publish(AdEvent event) {
        processor.process(event);
    }
}

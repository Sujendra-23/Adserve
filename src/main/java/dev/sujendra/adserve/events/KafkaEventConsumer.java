package dev.sujendra.adserve.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sujendra.adserve.model.AdEvent;
import dev.sujendra.adserve.service.EventProcessor;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Consumes the event stream and feeds the CTR model, so predictions learn from real traffic. */
@Component
@ConditionalOnProperty(name = "adserve.kafka.enabled", havingValue = "true")
public class KafkaEventConsumer {
    private final ObjectMapper mapper;
    private final EventProcessor processor;

    public KafkaEventConsumer(ObjectMapper mapper, EventProcessor processor) {
        this.mapper = mapper;
        this.processor = processor;
    }

    @KafkaListener(topics = "${adserve.kafka.topic:ad-events}", groupId = "adserve-ctr")
    public void onMessage(String payload) throws IOException {
        processor.process(mapper.readValue(payload, AdEvent.class));
    }
}

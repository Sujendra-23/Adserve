package dev.sujendra.adserve.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sujendra.adserve.model.AdEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Publishes events to Kafka, keyed by campaign id so one campaign's events stay in order on one partition. */
@Component
@ConditionalOnProperty(name = "adserve.kafka.enabled", havingValue = "true")
public class KafkaEventPublisher implements EventPublisher {
    private final KafkaTemplate<String, String> template;
    private final ObjectMapper mapper;
    private final String topic;

    public KafkaEventPublisher(KafkaTemplate<String, String> template, ObjectMapper mapper,
                               @Value("${adserve.kafka.topic:ad-events}") String topic) {
        this.template = template;
        this.mapper = mapper;
        this.topic = topic;
    }

    @Override
    public void publish(AdEvent event) {
        try {
            template.send(topic, event.campaignId(), mapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("could not serialize event", e);
        }
    }
}

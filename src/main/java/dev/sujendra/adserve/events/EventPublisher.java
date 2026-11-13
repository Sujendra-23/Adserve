package dev.sujendra.adserve.events;

import dev.sujendra.adserve.model.AdEvent;

public interface EventPublisher {
    void publish(AdEvent event);
}

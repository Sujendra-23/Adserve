package dev.sujendra.adserve.service;

import dev.sujendra.adserve.model.AdEvent;
import org.springframework.stereotype.Component;

@Component
public class EventProcessor {
    private final CtrModel ctrModel;

    public EventProcessor(CtrModel ctrModel) {
        this.ctrModel = ctrModel;
    }

    public void process(AdEvent event) {
        switch (event.type()) {
            case IMPRESSION -> ctrModel.recordImpression(event.campaignId());
            case CLICK -> ctrModel.recordClick(event.campaignId());
        }
    }
}

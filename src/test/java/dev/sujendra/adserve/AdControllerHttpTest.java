package dev.sujendra.adserve;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sujendra.adserve.model.Campaign;
import dev.sujendra.adserve.service.CampaignStore;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"adserve.sample-data=false", "adserve.kafka.enabled=false"})
class AdControllerHttpTest {
    @Autowired TestRestTemplate rest;
    @Autowired CampaignStore store;

    @BeforeEach
    void seed() {
        store.put(new Campaign("it-1", "Acme", "Try Acme", 40, 100, Set.of("it-search"), Set.of(), Set.of()));
    }

    private ResponseEntity<String> post(String path, String json) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return rest.postForEntity(path, new HttpEntity<>(json, h), String.class);
    }

    @Test
    void decideReturnsWinningAd() {
        ResponseEntity<String> r = post("/v1/ads/decide", "{\"placement\":\"it-search\"}");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("\"campaignId\":\"it-1\"").contains("priceCents");
    }

    @Test
    void noEligibleAdReturns204() {
        ResponseEntity<String> r = post("/v1/ads/decide", "{\"placement\":\"nowhere\"}");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void requestWithUserIdentifierIsRejected() {
        // Privacy by design: the API has no field for a user id, and unknown fields are a 400, not silently ignored.
        ResponseEntity<String> r = post("/v1/ads/decide", "{\"placement\":\"it-search\",\"userId\":\"u-123\"}");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void blankPlacementIsRejected() {
        ResponseEntity<String> r = post("/v1/ads/decide", "{\"placement\":\"\"}");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void eventsFeedTheAggregateReport() {
        assertThat(post("/v1/events", "{\"campaignId\":\"it-1\",\"type\":\"IMPRESSION\"}").getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);
        assertThat(post("/v1/events", "{\"campaignId\":\"it-1\",\"type\":\"CLICK\"}").getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);
        @SuppressWarnings("unchecked")
        Map<String, Object> report = rest.getForObject("/v1/campaigns/it-1/report", Map.class);
        assertThat(report.get("impressions")).isEqualTo(1);
        assertThat(report.get("clicks")).isEqualTo(1);
    }

    @Test
    void eventForUnknownCampaignIs404() {
        assertThat(post("/v1/events", "{\"campaignId\":\"nope\",\"type\":\"CLICK\"}").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}

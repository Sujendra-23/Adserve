package dev.sujendra.adserve.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** An impression or click. Aggregate-only: campaign, placement, and type, never who saw it. */
public record AdEvent(@NotBlank String campaignId, String placement, @NotNull Type type) {
    public enum Type { IMPRESSION, CLICK }
}

package dev.sujendra.adserve.model;

import java.util.Set;

/**
 * An ad campaign. Targeting is contextual only (placement, locale, category); there is no user field anywhere in the model.
 * Money is held in integer cents so budget math is exact.
 */
public record Campaign(
        String id,
        String advertiser,
        String creative,
        long bidCents,
        long dailyBudgetCents,
        Set<String> placements,
        Set<String> locales,
        Set<String> categories) {

    public Campaign {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id is required");
        if (bidCents <= 0) throw new IllegalArgumentException("bid must be positive");
        if (dailyBudgetCents < 0) throw new IllegalArgumentException("budget must not be negative");
        placements = placements == null ? Set.of() : Set.copyOf(placements);
        locales = locales == null ? Set.of() : Set.copyOf(locales);
        categories = categories == null ? Set.of() : Set.copyOf(categories);
    }

    /** Empty target sets mean "match anything". */
    public boolean matches(AdRequest r) {
        return (placements.isEmpty() || placements.contains(r.placement()))
                && (locales.isEmpty() || r.locale() == null || locales.contains(r.locale()))
                && (categories.isEmpty() || r.category() == null || categories.contains(r.category()));
    }
}

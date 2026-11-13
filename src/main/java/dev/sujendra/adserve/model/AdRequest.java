package dev.sujendra.adserve.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Contextual ad request. By design it carries no user identifier, device id or IP address;
 * the API rejects unknown fields so a client cannot slip one in.
 */
public record AdRequest(@NotBlank String placement, String locale, String category) {}

package io.effects.samples.ecommerce.domain.models;

/**
 * Domain register representing terms for B2B Bulk Order negotiations.
 */
public record BulkOrderTerms(int quantity, double unitPrice, double discountPercentage) {}

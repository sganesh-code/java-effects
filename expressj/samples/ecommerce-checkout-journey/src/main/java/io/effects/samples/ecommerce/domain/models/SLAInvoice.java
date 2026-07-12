package io.effects.samples.ecommerce.domain.models;

/**
 * Domain register representing an SLA rated invoice.
 */
public record SLAInvoice(double totalOverageFee, String currency) {}

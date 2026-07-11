package io.effects.samples.ecommerce.domain.models;

/**
 * Domain record representing an SLA rated invoice.
 */
public record SLAInvoice(double totalOverageFee, String currency) {}

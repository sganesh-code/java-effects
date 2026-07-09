package io.effects.samples.ecommerce.meterable;

import io.effects.Either;
import io.effects.recipes.meterable.MeterableRequest;
import io.effects.recipes.meterable.MeterLedger;
import io.effects.recipes.meterable.UsageStep;
import java.time.Instant;

public class PremiumSupportUsageMeter implements MeterableRequest<String, DiagnosticMetric, SLAInvoice> {

    @Override
    public Either<String, Void> evaluateUsage(MeterLedger<String, DiagnosticMetric> ledger, DiagnosticMetric metric, Instant now) {
        if (metric.diagnosticCallsCount() < 0 || metric.telemetryPayloadKb() < 0) {
            return Either.left("Diagnostic and telemetry metric measurements must be non-negative.");
        }
        return Either.right(null);
    }

    @Override
    public Either<String, SLAInvoice> evaluateRating(MeterLedger<String, DiagnosticMetric> ledger, Instant now) {
        int totalCalls = 0;
        int totalPayload = 0;
        for (UsageStep<DiagnosticMetric> step : ledger.history()) {
            totalCalls += step.metric().diagnosticCallsCount();
            totalPayload += step.metric().telemetryPayloadKb();
        }

        // Pricing model: first 5 calls are free, then $10.0 per call. First 1000kb telemetry payload is free, then $0.05 per kb.
        double callsOverage = Math.max(0, totalCalls - 5) * 10.0;
        double payloadOverage = Math.max(0, totalPayload - 1000) * 0.05;
        double totalOverage = callsOverage + payloadOverage;

        return Either.right(new SLAInvoice(totalOverage, "USD"));
    }
}

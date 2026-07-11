package io.effects.samples.ecommerce.domain;

import io.effects.Either;
import io.effects.recipes.meterable.*;
import io.effects.samples.ecommerce.domain.models.DiagnosticMetric;
import io.effects.samples.ecommerce.domain.models.SLAInvoice;
import java.time.Instant;

public class SLAUsageTracker implements MeterableRequest<String, DiagnosticMetric, SLAInvoice> {
    private final String customerEmail;
    private final MeterableProcess<String, DiagnosticMetric, SLAInvoice> meterProcess;

    public SLAUsageTracker(String customerEmail) {
        this.customerEmail = customerEmail;
        this.meterProcess = new MeterableProcess<>();
    }

    public void initiate() {
        DomainLogger.info("[METER] Initiating support usage metrics for: " + customerEmail);
        meterProcess.register(customerEmail, this).unsafeRunSync();
        meterProcess.initiate(customerEmail).unsafeRunSync();
    }

    public void start(Instant time) {
        meterProcess.start(customerEmail, time).unsafeRunSync();
    }

    public void recordUsage(DiagnosticMetric metric, Instant time) {
        var res = meterProcess.recordUsage(customerEmail, metric, time).unsafeRunSync();
        if (res.isLeft()) {
            throw new RuntimeException("Record usage failed: " + res.getLeft());
        }
    }

    public SLAInvoice rate(Instant time) {
        var res = meterProcess.rate(customerEmail, time).unsafeRunSync();
        if (res.isLeft()) {
            throw new RuntimeException("Rating failed: " + res.getLeft());
        }
        SLAInvoice invoice = res.getRight();
        DomainLogger.info("[METER] Rated Invoice calculated: Total support overage fee is $" + invoice.totalOverageFee() + " " + invoice.currency());
        return invoice;
    }

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

        double callsOverage = Math.max(0, totalCalls - 5) * 10.0;
        double payloadOverage = Math.max(0, totalPayload - 1000) * 0.05;
        double totalOverage = callsOverage + payloadOverage;

        return Either.right(new SLAInvoice(totalOverage, "USD"));
    }
}

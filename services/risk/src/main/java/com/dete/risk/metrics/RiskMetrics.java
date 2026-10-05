package com.dete.risk.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class RiskMetrics {

  private final MeterRegistry registry;

  public RiskMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  public void recordOrderRejected(String reason) {
    Counter.builder("orders_rejected_total")
        .description("Total number of orders rejected by risk validation")
        .tag("reason", reason != null ? reason : "UNKNOWN")
        .register(registry)
        .increment();
  }

  public void recordRiskCheck(String checkName, long durationNanos) {
    Timer.builder("risk_check_duration_seconds")
        .description("Pre-trade risk check duration in seconds")
        .tag("check", checkName != null ? checkName : "ALL")
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(registry)
        .record(durationNanos, TimeUnit.NANOSECONDS);
  }
}

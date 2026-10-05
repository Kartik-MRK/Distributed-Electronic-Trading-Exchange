package com.dete.auth.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class AuthMetrics {

  private final MeterRegistry registry;

  public AuthMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  public void recordUserRegistered() {
    Counter.builder("users_registered_total")
        .description("Total number of newly registered trader accounts")
        .register(registry)
        .increment();
  }

  public void recordLoginSuccess() {
    Counter.builder("users_login_total")
        .description("Total successful user authentications")
        .tag("status", "SUCCESS")
        .register(registry)
        .increment();
  }

  public void recordLoginFailure(String reason) {
    Counter.builder("users_login_total")
        .description("Total user authentication attempts")
        .tag("status", "FAILED")
        .tag("reason", reason != null ? reason : "UNKNOWN")
        .register(registry)
        .increment();
  }

  public Timer.Sample startDbTimer() {
    return Timer.start(registry);
  }

  public void stopDbTimer(Timer.Sample sample, String queryType) {
    sample.stop(
        Timer.builder("db_query_latency_seconds")
            .description("Database query execution latency in seconds")
            .tag("query", queryType)
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry));
  }
}

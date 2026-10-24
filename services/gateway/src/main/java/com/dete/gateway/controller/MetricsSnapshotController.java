package com.dete.gateway.controller;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/internal/metrics")
public class MetricsSnapshotController {

  private static final Logger log = LoggerFactory.getLogger(MetricsSnapshotController.class);

  private final WebClient webClient;

  private final Map<String, ServiceTarget> serviceTargets = new ConcurrentHashMap<>();

  public MetricsSnapshotController(
      WebClient.Builder webClientBuilder,
      @Value("${AUTH_SERVICE_URL:http://localhost:8081}") String authUrl,
      @Value("${ACCOUNT_SERVICE_URL:http://localhost:8082}") String accountUrl,
      @Value("${ORDER_SERVICE_URL:http://localhost:8083}") String orderUrl,
      @Value("${MATCHING_ENGINE_URL:http://localhost:8084}") String matchingEngineUrl,
      @Value("${RISK_SERVICE_URL:http://localhost:8085}") String riskUrl,
      @Value("${AUDIT_SERVICE_URL:http://localhost:8086}") String auditUrl,
      @Value("${MARKET_DATA_SERVICE_URL:http://localhost:8087}") String marketDataUrl,
      @Value("${SIMULATOR_SERVICE_URL:http://localhost:8089}") String simulatorUrl) {

    this.webClient = webClientBuilder.build();

    serviceTargets.put("gateway", new ServiceTarget("gateway", 8080, "http://localhost:8080"));
    serviceTargets.put("auth", new ServiceTarget("auth", 8081, authUrl));
    serviceTargets.put("account", new ServiceTarget("account", 8082, accountUrl));
    serviceTargets.put("order", new ServiceTarget("order", 8083, orderUrl));
    serviceTargets.put(
        "matching-engine", new ServiceTarget("matching-engine", 8084, matchingEngineUrl));
    serviceTargets.put("risk", new ServiceTarget("risk", 8085, riskUrl));
    serviceTargets.put("audit", new ServiceTarget("audit", 8086, auditUrl));
    serviceTargets.put("market-data", new ServiceTarget("market-data", 8087, marketDataUrl));
    serviceTargets.put("simulator", new ServiceTarget("simulator", 8089, simulatorUrl));
  }

  @GetMapping("/snapshot")
  public Mono<ResponseEntity<Map<String, Object>>> getSnapshot() {
    return Flux.fromIterable(serviceTargets.values())
        .flatMap(target -> checkHealth(target.name(), target.url(), target.port()))
        .collectList()
        .map(
            healthList -> {
              Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
              snapshot.put("timestamp", Instant.now().toEpochMilli());
              // Only service health is currently collected. Missing telemetry is not a zero value.
              snapshot.put("matchingThroughput", null);
              snapshot.put("e2eLatency", null);
              snapshot.put("kafkaConsumerLag", List.of());
              snapshot.put("servicesHealth", healthList);
              snapshot.put("activeWebsocketConnections", null);
              snapshot.put("jvmMemory", List.of());
              return ResponseEntity.ok(snapshot);
            });
  }

  private Mono<Map<String, Object>> checkHealth(String service, String baseUrl, int port) {
    return webClient
        .get()
        .uri(baseUrl + "/actuator/health")
        .retrieve()
        .bodyToMono(Map.class)
        .timeout(Duration.ofMillis(800))
        .map(
            body -> {
              String status =
                  body.get("status") != null ? body.get("status").toString() : "UNKNOWN";
              return Map.<String, Object>of("service", service, "status", status, "port", port);
            })
        .onErrorReturn(Map.of("service", service, "status", "UNKNOWN", "port", port));
  }

  private record ServiceTarget(String name, int port, String url) {}
}

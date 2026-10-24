package com.dete.gateway.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class MetricsSnapshotControllerTest {
  @Test
  void unavailableMeasurementsAreNeverReplacedWithInventedNumbers() {
    WebClient.Builder client =
        WebClient.builder()
            .exchangeFunction(
                request ->
                    Mono.just(
                        ClientResponse.create(HttpStatus.OK)
                            .header("Content-Type", "application/json")
                            .body("{\"status\":\"DOWN\"}")
                            .build()));
    String url = "http://unused.test";
    var response =
        new MetricsSnapshotController(client, url, url, url, url, url, url, url, url)
            .getSnapshot()
            .block();
    assertThat(response).isNotNull();
    Map<String, Object> body = response.getBody();
    assertThat(body)
        .containsEntry("matchingThroughput", null)
        .containsEntry("e2eLatency", null)
        .containsEntry("activeWebsocketConnections", null);
    assertThat((List<?>) body.get("jvmMemory")).isEmpty();
    List<Map<String, Object>> health = (List<Map<String, Object>>) body.get("servicesHealth");
    assertThat(health)
        .hasSize(9)
        .allSatisfy(service -> assertThat(service.get("status")).isEqualTo("DOWN"));
  }
}

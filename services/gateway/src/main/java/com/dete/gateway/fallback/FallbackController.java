package com.dete.gateway.fallback;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

  @RequestMapping(value = "/order-service", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Map<String, Object>>> orderServiceFallback() {
    return Mono.just(
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(
                Map.of(
                    "status", 503,
                    "error", "ORDER_SERVICE_UNAVAILABLE",
                    "message", "ORDER_SERVICE_UNAVAILABLE")));
  }

  @RequestMapping(value = "/account-service", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Map<String, Object>>> accountServiceFallback() {
    return Mono.just(
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(
                Map.of(
                    "status", 503,
                    "error", "ACCOUNT_SERVICE_UNAVAILABLE",
                    "message", "ACCOUNT_SERVICE_UNAVAILABLE")));
  }

  @RequestMapping(value = "/auth-service", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Map<String, Object>>> authServiceFallback() {
    return Mono.just(
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(
                Map.of(
                    "status", 503,
                    "error", "AUTH_SERVICE_UNAVAILABLE",
                    "message", "AUTH_SERVICE_UNAVAILABLE")));
  }

  @RequestMapping(value = "/market-data-service", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Map<String, Object>>> marketDataServiceFallback() {
    return Mono.just(
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(
                Map.of(
                    "status", 503,
                    "error", "MARKET_DATA_SERVICE_UNAVAILABLE",
                    "message", "MARKET_DATA_SERVICE_UNAVAILABLE")));
  }

  @RequestMapping(value = "/audit-service", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Map<String, Object>>> auditServiceFallback() {
    return Mono.just(
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(
                Map.of(
                    "status", 503,
                    "error", "AUDIT_SERVICE_UNAVAILABLE",
                    "message", "AUDIT_SERVICE_UNAVAILABLE")));
  }
}

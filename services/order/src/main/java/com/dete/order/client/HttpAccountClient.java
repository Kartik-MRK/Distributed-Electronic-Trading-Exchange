package com.dete.order.client;

import com.dete.order.exception.InsufficientFundsException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Component
public class HttpAccountClient implements AccountClient {

  private static final Logger log = LoggerFactory.getLogger(HttpAccountClient.class);

  private final RestTemplate restTemplate;
  private final String accountServiceUrl;

  public HttpAccountClient(
      RestTemplateBuilder restTemplateBuilder,
      @Value("${order.account-service.url:http://localhost:8082}") String accountServiceUrl) {
    this.restTemplate = restTemplateBuilder.build();
    this.accountServiceUrl = accountServiceUrl;
  }

  @Override
  @CircuitBreaker(name = "accountService", fallbackMethod = "reserveFundsFallback")
  public boolean reserveFunds(UUID accountId, UUID orderId, String asset, long amount) {
    String url = accountServiceUrl + "/internal/accounts/reserve";
    Map<String, Object> request =
        Map.of(
            "accountId", accountId,
            "orderId", orderId,
            "asset", asset,
            "amount", amount);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

    try {
      ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
      return response.getStatusCode().is2xxSuccessful();
    } catch (HttpClientErrorException.BadRequest e) {
      log.warn(
          "Account reservation rejected for account {} order {}: {}",
          accountId,
          orderId,
          e.getResponseBodyAsString());
      throw new InsufficientFundsException("Insufficient available balance for " + asset);
    } catch (Exception e) {
      log.error("Failed to call account service for fund reservation", e);
      throw e;
    }
  }

  @Override
  @CircuitBreaker(name = "accountService", fallbackMethod = "releaseFundsFallback")
  public boolean releaseFunds(UUID accountId, UUID orderId, String asset, long amount) {
    String url = accountServiceUrl + "/internal/accounts/release";
    Map<String, Object> request =
        Map.of(
            "accountId", accountId,
            "orderId", orderId,
            "asset", asset,
            "amount", amount);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

    try {
      ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
      return response.getStatusCode().is2xxSuccessful();
    } catch (Exception e) {
      log.error("Failed to call account service for fund release", e);
      throw e;
    }
  }

  public boolean reserveFundsFallback(
      UUID accountId, UUID orderId, String asset, long amount, Throwable t) {
    if (t instanceof InsufficientFundsException) {
      throw (InsufficientFundsException) t;
    }
    log.error("Circuit breaker triggered for account reservation: {}", t.getMessage());
    throw new IllegalStateException(
        "Account service is temporarily unavailable: " + t.getMessage(), t);
  }

  public boolean releaseFundsFallback(
      UUID accountId, UUID orderId, String asset, long amount, Throwable t) {
    log.error("Circuit breaker triggered for account release: {}", t.getMessage());
    throw new IllegalStateException(
        "Account service is temporarily unavailable: " + t.getMessage(), t);
  }
}

package com.dete.auth.client;

import com.dete.common.domain.types.FixedPoint;
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
import org.springframework.web.client.RestTemplate;

@Component
public class AccountServiceClient {

  private static final Logger log = LoggerFactory.getLogger(AccountServiceClient.class);

  private final RestTemplate restTemplate;
  private final String accountServiceUrl;
  private final boolean fundingEnabled;

  public AccountServiceClient(
      RestTemplateBuilder restTemplateBuilder,
      @Value("${auth.account-service-url:${ACCOUNT_SERVICE_URL:http://localhost:8082}}")
          String accountServiceUrl,
      @Value("${auth.welcome-funding.enabled:true}") boolean fundingEnabled) {
    this.restTemplate = restTemplateBuilder.build();
    this.accountServiceUrl = accountServiceUrl;
    this.fundingEnabled = fundingEnabled;
  }

  /**
   * Pre-funds a newly registered or guest trader account with initial welcome test funds (50,000
   * USDT and 50,000 USD).
   */
  public void fundInitialAccount(UUID accountId) {
    if (!fundingEnabled) {
      log.info("Welcome funding is disabled for account {}", accountId);
      return;
    }

    long amountNanos = 50_000L * FixedPoint.SCALE; // 50,000 * 10^8

    // Deposit USDT (matching frontend display)
    depositAsset(accountId, "USDT", amountNanos);

    // Deposit USD (matching exchange trading quote currency BTC-USD / ETH-USD)
    depositAsset(accountId, "USD", amountNanos);
  }

  private void depositAsset(UUID accountId, String asset, long amountNanos) {
    String url = accountServiceUrl + "/accounts/deposit";
    Map<String, Object> request =
        Map.of(
            "accountId", accountId,
            "asset", asset,
            "amount", amountNanos,
            "referenceId", UUID.randomUUID());

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

    try {
      ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
      if (response.getStatusCode().is2xxSuccessful()) {
        log.info(
            "Pre-funded account {} with {} {} (nanos: {})", accountId, 50_000, asset, amountNanos);
      } else {
        log.warn(
            "Deposit of {} for account {} returned non-2xx status: {}",
            asset,
            accountId,
            response.getStatusCode());
      }
    } catch (Exception e) {
      log.warn(
          "Could not deposit {} to account {} via {}: {} (account can still trade demo data)",
          asset,
          accountId,
          url,
          e.getMessage());
    }
  }
}

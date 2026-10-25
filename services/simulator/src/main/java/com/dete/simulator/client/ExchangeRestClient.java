package com.dete.simulator.client;

import com.dete.common.domain.enums.Instrument;
import com.dete.simulator.config.SimulatorProperties;
import com.dete.simulator.dto.AuthDtos;
import com.dete.simulator.dto.CreateOrderDto;
import com.dete.simulator.dto.DepositDto;
import com.dete.simulator.dto.OrderResponseDto;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ExchangeRestClient {

  private static final Logger log = LoggerFactory.getLogger(ExchangeRestClient.class);

  private final SimulatorProperties properties;
  private final RestClient restClient;

  @org.springframework.beans.factory.annotation.Autowired
  public ExchangeRestClient(SimulatorProperties properties) {
    this.properties = properties;
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(java.time.Duration.ofSeconds(2));
    requestFactory.setReadTimeout(java.time.Duration.ofSeconds(2));
    this.restClient = RestClient.builder().requestFactory(requestFactory).build();
  }

  public ExchangeRestClient(SimulatorProperties properties, RestClient restClient) {
    this.properties = properties;
    this.restClient = restClient;
  }

  public AuthDtos.AuthResponse login(String username, String password) {
    try {
      return restClient
          .post()
          .uri(properties.getAuthUrl() + "/auth/login")
          .contentType(MediaType.APPLICATION_JSON)
          .body(new AuthDtos.LoginRequest(username, password))
          .retrieve()
          .body(AuthDtos.AuthResponse.class);
    } catch (Exception e) {
      log.debug("Login failed for user '{}': {}", username, e.getMessage());
      return null;
    }
  }

  public boolean register(String username, String email, String password) {
    try {
      restClient
          .post()
          .uri(properties.getAuthUrl() + "/auth/register")
          .contentType(MediaType.APPLICATION_JSON)
          .body(new AuthDtos.RegisterRequest(username, email, password))
          .retrieve()
          .toBodilessEntity();
      log.info("Registered user '{}' ({})", username, email);
      return true;
    } catch (Exception e) {
      log.debug("Registration for user '{}' returned: {}", username, e.getMessage());
      return false;
    }
  }

  public boolean deposit(UUID accountId, String asset, long amountNanos, UUID referenceId) {
    try {
      restClient
          .post()
          .uri(properties.getAccountUrl() + "/internal/accounts/deposit")
          .contentType(MediaType.APPLICATION_JSON)
          .body(new DepositDto(accountId, asset, amountNanos, referenceId))
          .retrieve()
          .toBodilessEntity();
      log.debug("Deposited {} {} to account {}", amountNanos, asset, accountId);
      return true;
    } catch (Exception e) {
      log.warn(
          "Deposit failed for account {} ({}, {}): {}",
          accountId,
          asset,
          amountNanos,
          e.getMessage());
      return false;
    }
  }

  public long getRiskReferencePrice(Instrument instrument) {
    try {
      var response =
          restClient
              .get()
              .uri(properties.getRiskUrl() + "/risk/instruments")
              .retrieve()
              .body(new ParameterizedTypeReference<java.util.Map<String, Long>>() {});
      return response != null ? response.getOrDefault(instrument.name(), 0L) : 0L;
    } catch (Exception e) {
      log.warn("Could not read risk reference price for {}: {}", instrument, e.getMessage());
      return 0L;
    }
  }

  public OrderResponseDto placeOrder(
      String accessToken, CreateOrderDto orderRequest, UUID idempotencyKey) {
    try {
      UUID key = idempotencyKey != null ? idempotencyKey : UUID.randomUUID();
      return restClient
          .post()
          .uri(properties.getOrderUrl() + "/orders")
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
          .header("Idempotency-Key", key.toString())
          .contentType(MediaType.APPLICATION_JSON)
          .body(orderRequest)
          .retrieve()
          .body(OrderResponseDto.class);
    } catch (Exception e) {
      log.debug(
          "Failed to place order ({} {} {} @ {}): {}",
          orderRequest.side(),
          orderRequest.quantity(),
          orderRequest.instrument(),
          orderRequest.price(),
          e.getMessage());
      return null;
    }
  }

  public boolean cancelOrder(String accessToken, UUID orderId) {
    try {
      restClient
          .delete()
          .uri(properties.getOrderUrl() + "/orders/" + orderId)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
          .retrieve()
          .toBodilessEntity();
      log.debug("Cancelled order {}", orderId);
      return true;
    } catch (Exception e) {
      log.debug("Failed to cancel order {}: {}", orderId, e.getMessage());
      return false;
    }
  }

  public List<OrderResponseDto> getActiveOrders(String accessToken) {
    try {
      List<OrderResponseDto> orders =
          restClient
              .get()
              .uri(properties.getOrderUrl() + "/orders")
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
              .retrieve()
              .body(new ParameterizedTypeReference<List<OrderResponseDto>>() {});
      return orders != null ? orders : Collections.emptyList();
    } catch (Exception e) {
      log.debug("Failed to fetch active orders: {}", e.getMessage());
      return Collections.emptyList();
    }
  }

  public List<OrderResponseDto> getActiveOrdersStrict(String accessToken) {
    List<OrderResponseDto> orders =
        restClient
            .get()
            .uri(properties.getOrderUrl() + "/orders")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .retrieve()
            .body(new ParameterizedTypeReference<List<OrderResponseDto>>() {});
    if (orders == null)
      throw new IllegalStateException("Order service returned no active-order response");
    return orders;
  }
}

package com.dete.order.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.domain.enums.OrderType;
import com.dete.common.domain.types.FixedPoint;
import com.dete.order.controller.GlobalExceptionHandler;
import com.dete.order.dto.CreateOrderRequest;
import com.dete.order.dto.ErrorResponse;
import com.dete.order.exception.AccountServiceUnavailableException;
import com.dete.order.exception.InsufficientFundsException;
import com.dete.order.exception.RiskServiceUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.grpc.ManagedChannel;
import java.net.ConnectException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class CircuitBreakerFallbackUnitTest {

  private HttpAccountClient accountClient;
  private GrpcRiskClient riskClient;
  private GlobalExceptionHandler exceptionHandler;

  @BeforeEach
  void setUp() {
    RestTemplateBuilder builder = new RestTemplateBuilder();
    accountClient = new HttpAccountClient(builder, "http://localhost:8082");

    ManagedChannel channel = Mockito.mock(ManagedChannel.class);
    riskClient = new GrpcRiskClient(channel, new ObjectMapper());

    exceptionHandler = new GlobalExceptionHandler();
  }

  @Test
  @DisplayName(
      "HttpAccountClient reserve fallback throws AccountServiceUnavailableException with ACCOUNT_SERVICE_UNAVAILABLE")
  void testAccountReserveFallback() {
    UUID accountId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                accountClient.reserveFundsFallback(
                    accountId, orderId, "USDT", 1000L, new ConnectException("Connection refused")))
        .isInstanceOf(AccountServiceUnavailableException.class)
        .hasMessageContaining("ACCOUNT_SERVICE_UNAVAILABLE");
  }

  @Test
  @DisplayName(
      "HttpAccountClient reserve fallback preserves InsufficientFundsException without masking")
  void testAccountReservePreservesInsufficientFunds() {
    UUID accountId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                accountClient.reserveFundsFallback(
                    accountId,
                    orderId,
                    "USDT",
                    1000L,
                    new InsufficientFundsException("Not enough USD")))
        .isInstanceOf(InsufficientFundsException.class)
        .hasMessageContaining("Not enough USD");
  }

  @Test
  @DisplayName(
      "GrpcRiskClient fallback throws RiskServiceUnavailableException with RISK_SERVICE_UNAVAILABLE")
  void testRiskFallback() {
    CreateOrderRequest request =
        new CreateOrderRequest(
            Instrument.BTC_USD,
            OrderSide.BUY,
            OrderType.LIMIT,
            65_000L * FixedPoint.SCALE,
            1L * FixedPoint.SCALE);

    assertThatThrownBy(
            () ->
                riskClient.riskFallback(
                    request, UUID.randomUUID(), new ConnectException("Risk service offline")))
        .isInstanceOf(RiskServiceUnavailableException.class)
        .hasMessageContaining("RISK_SERVICE_UNAVAILABLE");
  }

  @Test
  @DisplayName(
      "GlobalExceptionHandler maps RiskServiceUnavailableException to 503 with code RISK_SERVICE_UNAVAILABLE")
  void testExceptionHandlerRiskUnavailable() {
    RiskServiceUnavailableException ex =
        new RiskServiceUnavailableException("Connection timed out");
    ResponseEntity<ErrorResponse> response = exceptionHandler.handleRiskUnavailable(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().error()).isEqualTo("RISK_SERVICE_UNAVAILABLE");
  }

  @Test
  @DisplayName(
      "GlobalExceptionHandler maps AccountServiceUnavailableException to 503 with code ACCOUNT_SERVICE_UNAVAILABLE")
  void testExceptionHandlerAccountUnavailable() {
    AccountServiceUnavailableException ex =
        new AccountServiceUnavailableException("Service unreachable");
    ResponseEntity<ErrorResponse> response = exceptionHandler.handleAccountUnavailable(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().error()).isEqualTo("ACCOUNT_SERVICE_UNAVAILABLE");
  }

  @Test
  @DisplayName(
      "GlobalExceptionHandler maps CallNotPermittedException for open circuit breakers to 503")
  void testExceptionHandlerCallNotPermitted() {
    CircuitBreaker cb = CircuitBreaker.ofDefaults("riskService");
    CallNotPermittedException ex = CallNotPermittedException.createCallNotPermittedException(cb);

    ResponseEntity<ErrorResponse> response = exceptionHandler.handleCallNotPermitted(ex);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().error()).isEqualTo("RISK_SERVICE_UNAVAILABLE");
  }
}

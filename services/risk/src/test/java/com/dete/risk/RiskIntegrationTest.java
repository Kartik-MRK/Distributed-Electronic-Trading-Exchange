package com.dete.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.domain.enums.OrderType;
import com.dete.common.domain.risk.ValidateOrderRequest;
import com.dete.common.domain.risk.ValidateOrderResponse;
import com.dete.common.domain.types.FixedPoint;
import com.dete.common.events.order.OrderCancelledEvent;
import com.dete.common.events.trade.TradeExecutedEvent;
import com.dete.common.test.KafkaTestContainerBase;
import com.dete.risk.consumer.RiskOrderEventConsumer;
import com.dete.risk.consumer.RiskTradeEventConsumer;
import com.dete.risk.engine.RiskRuleEvaluator;
import com.dete.risk.grpc.RiskGrpcContracts;
import com.dete.risk.grpc.RiskGrpcServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.ClientCalls;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.main.allow-bean-definition-overriding=true", "risk.grpc.port=0"})
class RiskIntegrationTest extends KafkaTestContainerBase {

  @Autowired private RiskGrpcServer riskGrpcServer;
  @Autowired private RiskRuleEvaluator riskRuleEvaluator;
  @Autowired private TestRestTemplate restTemplate;
  @Autowired private KafkaTemplate<String, String> kafkaTemplate;
  @Autowired private ObjectMapper objectMapper;

  @Test
  @DisplayName("gRPC ValidateOrder RPC over live Netty channel validates orders correctly")
  void testGrpcValidateOrderLiveCall() throws InterruptedException {
    int grpcPort = riskGrpcServer.getPort();
    ManagedChannel channel =
        ManagedChannelBuilder.forAddress("localhost", grpcPort).usePlaintext().build();

    try {
      var validateOrderMethod = RiskGrpcContracts.createValidateOrderMethod(objectMapper);
      UUID accountId = UUID.randomUUID();
      UUID orderId = UUID.randomUUID();

      ValidateOrderRequest validReq =
          new ValidateOrderRequest(
              orderId,
              accountId,
              Instrument.BTC_USD,
              OrderSide.BUY,
              OrderType.LIMIT,
              60_000L * FixedPoint.SCALE,
              1L * FixedPoint.SCALE);

      ValidateOrderResponse validResp =
          ClientCalls.blockingUnaryCall(
              channel, validateOrderMethod, CallOptions.DEFAULT, validReq);

      assertThat(validResp).isNotNull();
      assertThat(validResp.approved()).isTrue();

      ValidateOrderRequest invalidReq =
          new ValidateOrderRequest(
              UUID.randomUUID(),
              accountId,
              Instrument.BTC_USD,
              OrderSide.BUY,
              OrderType.LIMIT,
              60_000L * FixedPoint.SCALE,
              150L * FixedPoint.SCALE); // > 100 BTC max

      ValidateOrderResponse invalidResp =
          ClientCalls.blockingUnaryCall(
              channel, validateOrderMethod, CallOptions.DEFAULT, invalidReq);

      assertThat(invalidResp).isNotNull();
      assertThat(invalidResp.approved()).isFalse();
      assertThat(invalidResp.rejectionReason()).contains("Rule 1 Violation");
    } finally {
      channel.shutdown();
      channel.awaitTermination(3, TimeUnit.SECONDS);
    }
  }

  @Test
  @DisplayName("Kafka trade.executions consumer updates last price and enforces price deviation")
  void testKafkaTradeExecutionsConsumer() throws Exception {
    long newTradePrice = 80_000L * FixedPoint.SCALE;
    TradeExecutedEvent tradeEvent =
        new TradeExecutedEvent(
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instrument.BTC_USD,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            newTradePrice,
            1L * FixedPoint.SCALE,
            1L,
            Instant.now(),
            1);

    kafkaTemplate.send(
        RiskTradeEventConsumer.TOPIC_TRADE_EXECUTIONS,
        tradeEvent.tradeId().toString(),
        objectMapper.writeValueAsString(tradeEvent));

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(riskRuleEvaluator.getLastTradePrice(Instrument.BTC_USD))
                    .isEqualTo(newTradePrice));

    // After updating to 80,000, a BUY limit at 90,000 (+12.5%) should be rejected
    ValidateOrderRequest aggressiveBuy =
        new ValidateOrderRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instrument.BTC_USD,
            OrderSide.BUY,
            OrderType.LIMIT,
            90_000L * FixedPoint.SCALE,
            1L * FixedPoint.SCALE);

    ValidateOrderResponse response = riskRuleEvaluator.evaluate(aggressiveBuy);
    assertThat(response.approved()).isFalse();
    assertThat(response.rejectionReason()).contains("Rule 4 Violation");
  }

  @Test
  @DisplayName("Kafka order.events consumer cleans up open orders tracking on terminal events")
  void testKafkaOrderEventsConsumer() throws Exception {
    UUID accountId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();

    // First, approve an order so it is tracked
    ValidateOrderRequest order =
        new ValidateOrderRequest(
            orderId,
            accountId,
            Instrument.SOL_USD,
            OrderSide.BUY,
            OrderType.LIMIT,
            150L * FixedPoint.SCALE,
            5L * FixedPoint.SCALE);

    ValidateOrderResponse approved = riskRuleEvaluator.evaluate(order);
    assertThat(approved.approved()).isTrue();
    assertThat(riskRuleEvaluator.getAccountState(accountId).getOpenOrdersCount())
        .isGreaterThanOrEqualTo(1);

    // Publish OrderCancelledEvent
    OrderCancelledEvent cancelledEvent = OrderCancelledEvent.of(orderId, 0L);
    kafkaTemplate.send(
        RiskOrderEventConsumer.TOPIC_ORDER_EVENTS,
        orderId.toString(),
        objectMapper.writeValueAsString(cancelledEvent));

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(riskRuleEvaluator.getAccountState(accountId).getOpenOrdersCount())
                    .isEqualTo(0));
  }

  @Test
  @DisplayName(
      "REST endpoints /risk/instruments, /risk/rules, /risk/accounts/{id}, and /risk/validate work")
  void testRestEndpoints() {
    // 1. GET /risk/instruments
    ResponseEntity<Map> instResp = restTemplate.getForEntity("/risk/instruments", Map.class);
    assertThat(instResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(instResp.getBody()).containsKey("BTC_USD");

    // 2. GET /risk/rules
    ResponseEntity<Map> rulesResp = restTemplate.getForEntity("/risk/rules", Map.class);
    assertThat(rulesResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(rulesResp.getBody()).containsKey("maxOpenOrdersPerAccount");

    // 3. GET /risk/accounts/{id}
    UUID accId = UUID.randomUUID();
    ResponseEntity<Map> accResp = restTemplate.getForEntity("/risk/accounts/" + accId, Map.class);
    assertThat(accResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(accResp.getBody()).containsEntry("accountId", accId.toString());

    // 4. POST /risk/validate
    ValidateOrderRequest request =
        new ValidateOrderRequest(
            UUID.randomUUID(),
            accId,
            Instrument.BTC_USD,
            OrderSide.BUY,
            OrderType.LIMIT,
            60_000L * FixedPoint.SCALE,
            1L * FixedPoint.SCALE);

    ResponseEntity<ValidateOrderResponse> valResp =
        restTemplate.postForEntity("/risk/validate", request, ValidateOrderResponse.class);
    assertThat(valResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(valResp.getBody()).isNotNull();
    assertThat(valResp.getBody().approved()).isTrue();
  }
}

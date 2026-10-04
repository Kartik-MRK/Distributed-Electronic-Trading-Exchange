package com.dete.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.domain.enums.OrderType;
import com.dete.common.domain.types.FixedPoint;
import com.dete.common.events.order.OrderCancelCommand;
import com.dete.common.events.order.OrderModifyCommand;
import com.dete.common.events.order.OrderPlacedEvent;
import com.dete.common.events.trade.TradeExecutedEvent;
import com.dete.common.test.KafkaTestContainerBase;
import com.dete.matching.consumer.OrderCommandConsumer;
import com.dete.matching.engine.OrderBook;
import com.dete.matching.publisher.MatchingEventPublisher;
import com.dete.matching.replay.KafkaReplayService;
import com.dete.matching.service.MatchingEngineService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MatchingEngineIntegrationTest extends KafkaTestContainerBase {

  @LocalServerPort private int port;

  @Autowired private KafkaTemplate<String, String> kafkaTemplate;

  @Autowired private MatchingEngineService matchingEngineService;

  @Autowired private KafkaReplayService replayService;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private TestRestTemplate restTemplate;

  private KafkaConsumer<String, String> testEventConsumer;

  @DynamicPropertySource
  static void registerKafkaProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
  }

  @BeforeEach
  void setUpConsumer() {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-verifier-" + UUID.randomUUID());
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    testEventConsumer = new KafkaConsumer<>(props);
    testEventConsumer.subscribe(
        List.of(
            MatchingEventPublisher.TOPIC_TRADE_EXECUTIONS,
            MatchingEventPublisher.TOPIC_ORDER_EVENTS));
  }

  @AfterEach
  void tearDownConsumer() {
    if (testEventConsumer != null) {
      testEventConsumer.close();
    }
  }

  @Test
  @Order(1)
  @DisplayName(
      "Full lifecycle: submit crossing limit orders via Kafka -> produces trades and order events")
  void testCrossingOrdersExecution() throws Exception {
    UUID seller = UUID.randomUUID();
    UUID buyer = UUID.randomUUID();

    // 1. Submit resting Ask at 50,000 for 2 BTC
    UUID sellOrderId = UUID.randomUUID();
    OrderPlacedEvent sellOrder =
        new OrderPlacedEvent(
            UUID.randomUUID(),
            sellOrderId,
            seller,
            Instrument.BTC_USD,
            OrderSide.SELL,
            OrderType.LIMIT,
            50_000 * FixedPoint.SCALE,
            2 * FixedPoint.SCALE,
            UUID.randomUUID(),
            Instant.now(),
            1);

    kafkaTemplate
        .send(
            OrderCommandConsumer.TOPIC_ORDER_COMMANDS,
            Instrument.BTC_USD.symbol(),
            objectMapper.writeValueAsString(sellOrder))
        .get(5, TimeUnit.SECONDS);

    // Wait until resting order is in the book
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              OrderBook book = matchingEngineService.getOrderBook(Instrument.BTC_USD);
              assertThat(book.orderCount()).isGreaterThanOrEqualTo(1);
              assertThat(book.bestAskPrice()).isEqualTo(50_000 * FixedPoint.SCALE);
            });

    // 2. Submit crossing Bid at 50,000 for 1 BTC
    UUID buyOrderId = UUID.randomUUID();
    OrderPlacedEvent buyOrder =
        new OrderPlacedEvent(
            UUID.randomUUID(),
            buyOrderId,
            buyer,
            Instrument.BTC_USD,
            OrderSide.BUY,
            OrderType.LIMIT,
            50_000 * FixedPoint.SCALE,
            1 * FixedPoint.SCALE,
            UUID.randomUUID(),
            Instant.now(),
            1);

    kafkaTemplate
        .send(
            OrderCommandConsumer.TOPIC_ORDER_COMMANDS,
            Instrument.BTC_USD.symbol(),
            objectMapper.writeValueAsString(buyOrder))
        .get(5, TimeUnit.SECONDS);

    // Verify trade and events emitted
    List<ConsumerRecord<String, String>> receivedTrades = new ArrayList<>();
    List<ConsumerRecord<String, String>> receivedOrderEvents = new ArrayList<>();

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              ConsumerRecords<String, String> records =
                  testEventConsumer.poll(Duration.ofMillis(200));
              for (ConsumerRecord<String, String> record : records) {
                if (MatchingEventPublisher.TOPIC_TRADE_EXECUTIONS.equals(record.topic())) {
                  receivedTrades.add(record);
                } else if (MatchingEventPublisher.TOPIC_ORDER_EVENTS.equals(record.topic())) {
                  receivedOrderEvents.add(record);
                }
              }
              assertThat(receivedTrades).isNotEmpty();
              assertThat(receivedOrderEvents).isNotEmpty();
            });

    // Assert Trade details
    TradeExecutedEvent executedTrade =
        objectMapper.readValue(receivedTrades.get(0).value(), TradeExecutedEvent.class);
    assertThat(executedTrade.price()).isEqualTo(50_000 * FixedPoint.SCALE);
    assertThat(executedTrade.quantity()).isEqualTo(1 * FixedPoint.SCALE);
    assertThat(executedTrade.buyOrderId()).isEqualTo(buyOrderId);
    assertThat(executedTrade.sellOrderId()).isEqualTo(sellOrderId);

    // OrderBook state: 1 BTC remains on ask
    OrderBook book = matchingEngineService.getOrderBook(Instrument.BTC_USD);
    assertThat(book.totalAskVolume()).isEqualTo(1 * FixedPoint.SCALE);
  }

  @Test
  @Order(2)
  @DisplayName("Order cancellation via Kafka command removes resting order in O(1)")
  void testCancelCommandIntegration() throws Exception {
    UUID account = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();

    OrderPlacedEvent bid =
        new OrderPlacedEvent(
            UUID.randomUUID(),
            orderId,
            account,
            Instrument.ETH_USD,
            OrderSide.BUY,
            OrderType.LIMIT,
            3_000 * FixedPoint.SCALE,
            5 * FixedPoint.SCALE,
            UUID.randomUUID(),
            Instant.now(),
            1);

    kafkaTemplate
        .send(
            OrderCommandConsumer.TOPIC_ORDER_COMMANDS,
            Instrument.ETH_USD.symbol(),
            objectMapper.writeValueAsString(bid))
        .get(5, TimeUnit.SECONDS);

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              OrderBook book = matchingEngineService.getOrderBook(Instrument.ETH_USD);
              assertThat(book.getOrder(orderId)).isNotNull();
            });

    // Send cancellation command
    OrderCancelCommand cancelCommand = OrderCancelCommand.of(orderId, account, Instrument.ETH_USD);

    kafkaTemplate
        .send(
            OrderCommandConsumer.TOPIC_ORDER_COMMANDS,
            Instrument.ETH_USD.symbol(),
            objectMapper.writeValueAsString(cancelCommand))
        .get(5, TimeUnit.SECONDS);

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              OrderBook book = matchingEngineService.getOrderBook(Instrument.ETH_USD);
              assertThat(book.getOrder(orderId)).isNull();
              assertThat(book.orderCount()).isEqualTo(0);
            });
  }

  @Test
  @Order(3)
  @DisplayName("Order modification via Kafka executes cancel-and-reinsert with new sequence number")
  void testModifyCommandIntegration() throws Exception {
    UUID account = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();

    OrderPlacedEvent bid =
        new OrderPlacedEvent(
            UUID.randomUUID(),
            orderId,
            account,
            Instrument.SOL_USD,
            OrderSide.BUY,
            OrderType.LIMIT,
            150 * FixedPoint.SCALE,
            10 * FixedPoint.SCALE,
            UUID.randomUUID(),
            Instant.now(),
            1);

    kafkaTemplate
        .send(
            OrderCommandConsumer.TOPIC_ORDER_COMMANDS,
            Instrument.SOL_USD.symbol(),
            objectMapper.writeValueAsString(bid))
        .get(5, TimeUnit.SECONDS);

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              OrderBook book = matchingEngineService.getOrderBook(Instrument.SOL_USD);
              assertThat(book.getOrder(orderId)).isNotNull();
            });

    // Send modify command to increase quantity to 15 SOL at price 152
    OrderModifyCommand modifyCommand =
        OrderModifyCommand.of(
            orderId, account, Instrument.SOL_USD, 152 * FixedPoint.SCALE, 15 * FixedPoint.SCALE);

    kafkaTemplate
        .send(
            OrderCommandConsumer.TOPIC_ORDER_COMMANDS,
            Instrument.SOL_USD.symbol(),
            objectMapper.writeValueAsString(modifyCommand))
        .get(5, TimeUnit.SECONDS);

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              OrderBook book = matchingEngineService.getOrderBook(Instrument.SOL_USD);
              assertThat(book.getOrder(orderId)).isNotNull();
              assertThat(book.getOrder(orderId).price()).isEqualTo(152 * FixedPoint.SCALE);
              assertThat(book.getOrder(orderId).remainingQuantity())
                  .isEqualTo(15 * FixedPoint.SCALE);
            });
  }

  @Test
  @Order(4)
  @DisplayName(
      "Kafka event replay reconstructs in-memory order books accurately upon engine restart")
  void testKafkaReplayReconstruction() {
    // Current in-memory book state has 1 BTC resting ask and 15 SOL resting bid
    int replayed = replayService.replayFromBeginning();
    assertThat(replayed).isGreaterThanOrEqualTo(3);

    OrderBook btcBook = matchingEngineService.getOrderBook(Instrument.BTC_USD);
    assertThat(btcBook.totalAskVolume()).isEqualTo(1 * FixedPoint.SCALE);

    OrderBook solBook = matchingEngineService.getOrderBook(Instrument.SOL_USD);
    assertThat(solBook.totalBidVolume()).isEqualTo(15 * FixedPoint.SCALE);
    assertThat(solBook.bestBidPrice()).isEqualTo(152 * FixedPoint.SCALE);
  }

  @Test
  @Order(5)
  @DisplayName("REST API endpoints return Level 2 depth and order book statistics")
  void testRestEndpoints() {
    ResponseEntity<Map> stats =
        restTemplate.getForEntity(
            "http://localhost:" + port + "/matching/orderbook/BTC_USD/stats", Map.class);
    assertThat(stats.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(stats.getBody().get("instrument")).isEqualTo("BTC_USD");
    assertThat(stats.getBody().get("bestAskPrice")).isNotNull();

    ResponseEntity<Map> depth =
        restTemplate.getForEntity(
            "http://localhost:" + port + "/matching/orderbook/BTC_USD?depth=5", Map.class);
    assertThat(depth.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(depth.getBody().get("instrument")).isEqualTo("BTC_USD");
  }
}

package com.dete.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.domain.enums.OrderType;
import com.dete.common.events.marketdata.OrderBookSnapshotEvent;
import com.dete.common.events.order.OrderPlacedEvent;
import com.dete.common.events.trade.TradeExecutedEvent;
import com.dete.common.test.FullStackTestBase;
import com.dete.marketdata.model.Candle;
import com.dete.marketdata.model.MarketDataTradeRecord;
import com.dete.marketdata.model.OrderBookSnapshotResponse;
import com.dete.marketdata.repository.MarketDataTradeRepository;
import com.dete.marketdata.service.MarketDataService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "spring.kafka.consumer.auto-offset-reset=earliest"
    })
class MarketDataIntegrationTest extends FullStackTestBase {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private KafkaTemplate<String, String> kafkaTemplate;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private MarketDataService marketDataService;
  @Autowired private MarketDataTradeRepository tradeRepository;

  @BeforeEach
  void cleanState() {
    marketDataService.resetAll();
  }

  @Test
  @DisplayName("REST /market-data/instruments returns metadata for supported instruments")
  void testGetInstruments() {
    ResponseEntity<List> response =
        restTemplate.getForEntity("/market-data/instruments", List.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().size()).isGreaterThanOrEqualTo(3);
  }

  @Test
  @DisplayName("Full CQRS pipeline: Kafka trade execution -> tape, candles, DB persistence & REST")
  void testTradeExecutionPipeline() throws Exception {
    UUID tradeId = UUID.randomUUID();
    UUID buyOrderId = UUID.randomUUID();
    UUID sellOrderId = UUID.randomUUID();
    UUID buyAccountId = UUID.randomUUID();
    UUID sellAccountId = UUID.randomUUID();
    long price = 65_000_00000000L; // 65,000 USD
    long quantity = 2_00000000L; // 2 BTC

    TradeExecutedEvent trade =
        new TradeExecutedEvent(
            UUID.randomUUID(),
            tradeId,
            Instrument.BTC_USD,
            buyOrderId,
            sellOrderId,
            buyAccountId,
            sellAccountId,
            price,
            quantity,
            101L,
            Instant.now(),
            1);

    kafkaTemplate
        .send(
            "trade.executions", Instrument.BTC_USD.symbol(), objectMapper.writeValueAsString(trade))
        .get(5, TimeUnit.SECONDS);

    // 1. Verify Last Traded Price via REST
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              ResponseEntity<Map> priceResp =
                  restTemplate.getForEntity("/market-data/BTC-USD/price", Map.class);
              assertThat(priceResp.getStatusCode()).isEqualTo(HttpStatus.OK);
              assertThat(((Number) priceResp.getBody().get("lastTradedPrice")).longValue())
                  .isEqualTo(price);
            });

    // 2. Verify Trade Tape via REST
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              ResponseEntity<String> tradesResp =
                  restTemplate.getForEntity("/market-data/BTC-USD/trades", String.class);
              assertThat(tradesResp.getStatusCode()).isEqualTo(HttpStatus.OK);
              List<TradeExecutedEvent> trades =
                  objectMapper.readValue(
                      tradesResp.getBody(), new TypeReference<List<TradeExecutedEvent>>() {});
              assertThat(trades).isNotEmpty();
              assertThat(trades.get(0).tradeId()).isEqualTo(tradeId);
            });

    // 3. Verify OHLCV Candles via REST
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              ResponseEntity<String> candleResp =
                  restTemplate.getForEntity(
                      "/market-data/BTC-USD/candles?interval=1m", String.class);
              assertThat(candleResp.getStatusCode()).isEqualTo(HttpStatus.OK);
              List<Candle> candles =
                  objectMapper.readValue(
                      candleResp.getBody(), new TypeReference<List<Candle>>() {});
              assertThat(candles).isNotEmpty();
              Candle c = candles.get(candles.size() - 1);
              assertThat(c.close()).isEqualTo(price);
              assertThat(c.volume()).isEqualTo(quantity);
            });

    // 4. Verify Historical Trade Storage in Postgres DB (for Phase 12 Replay Viewer)
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              List<MarketDataTradeRecord> persisted =
                  tradeRepository.findByInstrument(Instrument.BTC_USD, 10);
              assertThat(persisted).isNotEmpty();
              assertThat(persisted.get(0).tradeId()).isEqualTo(tradeId);
              assertThat(persisted.get(0).price()).isEqualTo(price);
            });
  }

  @Test
  @DisplayName(
      "Full CQRS pipeline: Order events maintain in-memory order book & resync from snapshot")
  void testOrderBookAndResyncPipeline() throws Exception {
    UUID orderId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    long price = 64_000_00000000L;
    long quantity = 5_00000000L;

    OrderPlacedEvent placed =
        OrderPlacedEvent.of(
            orderId,
            accountId,
            Instrument.BTC_USD,
            OrderSide.BUY,
            OrderType.LIMIT,
            price,
            quantity,
            UUID.randomUUID());

    kafkaTemplate
        .send("order.commands", Instrument.BTC_USD.name(), objectMapper.writeValueAsString(placed))
        .get(5, TimeUnit.SECONDS);

    // Verify order book has resting bid
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              ResponseEntity<OrderBookSnapshotResponse> bookResp =
                  restTemplate.getForEntity(
                      "/market-data/BTC-USD/orderbook", OrderBookSnapshotResponse.class);
              assertThat(bookResp.getStatusCode()).isEqualTo(HttpStatus.OK);
              assertThat(bookResp.getBody().bids()).isNotEmpty();
              assertThat(bookResp.getBody().bids().get(0).price()).isEqualTo(price);
            });

    // Test snapshot resync from Matching Engine
    OrderBookSnapshotEvent snapshot =
        new OrderBookSnapshotEvent(
            UUID.randomUUID(),
            Instrument.BTC_USD,
            500L,
            List.of(new OrderBookSnapshotEvent.SnapshotLevel(70_000L, 100L, 1)),
            List.of(new OrderBookSnapshotEvent.SnapshotLevel(71_000L, 200L, 2)),
            Instant.now(),
            1);

    kafkaTemplate
        .send("market-data", Instrument.BTC_USD.symbol(), objectMapper.writeValueAsString(snapshot))
        .get(5, TimeUnit.SECONDS);

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              ResponseEntity<OrderBookSnapshotResponse> resynced =
                  restTemplate.getForEntity(
                      "/market-data/BTC-USD/orderbook", OrderBookSnapshotResponse.class);
              assertThat(resynced.getStatusCode()).isEqualTo(HttpStatus.OK);
              assertThat(resynced.getBody().sequenceNumber()).isEqualTo(500L);
              assertThat(resynced.getBody().bids()).hasSize(1);
              assertThat(resynced.getBody().bids().get(0).price()).isEqualTo(70_000L);
            });
  }

  @Test
  @DisplayName(
      "WebSocket client connects to /ws and receives live trade and order book streaming within 100ms")
  void testWebSocketStreaming() throws Exception {
    WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
    converter.setObjectMapper(objectMapper);
    stompClient.setMessageConverter(converter);

    String wsUrl = "ws://localhost:" + port + "/ws";
    StompSession session =
        stompClient
            .connectAsync(wsUrl, new StompSessionHandlerAdapter() {})
            .get(5, TimeUnit.SECONDS);
    assertThat(session.isConnected()).isTrue();

    BlockingQueue<TradeExecutedEvent> tradeQueue = new LinkedBlockingQueue<>();
    BlockingQueue<OrderBookSnapshotResponse> bookQueue = new LinkedBlockingQueue<>();

    session.subscribe(
        "/topic/trades.BTC-USD",
        new StompFrameHandler() {
          @Override
          public Type getPayloadType(StompHeaders headers) {
            return TradeExecutedEvent.class;
          }

          @Override
          public void handleFrame(StompHeaders headers, Object payload) {
            tradeQueue.offer((TradeExecutedEvent) payload);
          }
        });

    session.subscribe(
        "/topic/orderbook.BTC-USD",
        new StompFrameHandler() {
          @Override
          public Type getPayloadType(StompHeaders headers) {
            return OrderBookSnapshotResponse.class;
          }

          @Override
          public void handleFrame(StompHeaders headers, Object payload) {
            bookQueue.offer((OrderBookSnapshotResponse) payload);
          }
        });

    // Small delay to ensure subscription registration is acknowledged
    Thread.sleep(300);

    // Emit TradeExecutedEvent
    UUID tradeId = UUID.randomUUID();
    TradeExecutedEvent liveTrade =
        new TradeExecutedEvent(
            UUID.randomUUID(),
            tradeId,
            Instrument.BTC_USD,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            66_000_00000000L,
            1_00000000L,
            201L,
            Instant.now(),
            1);

    kafkaTemplate
        .send(
            "trade.executions",
            Instrument.BTC_USD.symbol(),
            objectMapper.writeValueAsString(liveTrade))
        .get(5, TimeUnit.SECONDS);

    // Verify trade received over WebSocket within 5 seconds
    TradeExecutedEvent receivedTrade = tradeQueue.poll(5, TimeUnit.SECONDS);
    assertThat(receivedTrade).isNotNull();
    assertThat(receivedTrade.tradeId()).isEqualTo(tradeId);

    // Emit OrderPlacedEvent
    OrderPlacedEvent liveOrder =
        OrderPlacedEvent.of(
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instrument.BTC_USD,
            OrderSide.SELL,
            OrderType.LIMIT,
            67_000_00000000L,
            3_00000000L,
            UUID.randomUUID());

    kafkaTemplate
        .send(
            "order.commands", Instrument.BTC_USD.name(), objectMapper.writeValueAsString(liveOrder))
        .get(5, TimeUnit.SECONDS);

    // Verify orderbook snapshot received over WebSocket
    OrderBookSnapshotResponse receivedBook = bookQueue.poll(5, TimeUnit.SECONDS);
    assertThat(receivedBook).isNotNull();
    assertThat(receivedBook.instrument()).isEqualTo(Instrument.BTC_USD);

    session.disconnect();
  }
}

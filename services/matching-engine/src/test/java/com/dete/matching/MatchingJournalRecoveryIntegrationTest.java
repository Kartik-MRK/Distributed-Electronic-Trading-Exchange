package com.dete.matching;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.dete.common.domain.enums.*;
import com.dete.common.events.order.OrderPlacedEvent;
import com.dete.common.test.SimulationTestSupport;
import com.dete.matching.consumer.OrderCommandConsumer;
import com.dete.matching.publisher.MatchingEventPublisher;
import com.dete.matching.replay.KafkaReplayService;
import com.dete.matching.service.MatchingEngineService;
import com.dete.simulation.control.SimulationControlRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Exercise the real admission/output journal at each crash boundary, with a fresh matcher. */
@Testcontainers
class MatchingJournalRecoveryIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

  final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
  JdbcTemplate jdbc;
  SimulationControlRepository control;

  @BeforeEach
  void schema() {
    var ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
    jdbc = new JdbcTemplate(ds);
    SimulationTestSupport.initialize(jdbc);
    jdbc.execute(
        "TRUNCATE simulator.matching_output,simulator.matching_admission,simulator.market_clock,simulator.simulation_session CASCADE");
    SimulationTestSupport.initialize(jdbc);
    control =
        new SimulationControlRepository(
            jdbc, new TransactionTemplate(new DataSourceTransactionManager(ds)));
  }

  OrderCommandConsumer consumer(MatchingEngineService engine, MatchingEventPublisher publisher) {
    var consumer = new OrderCommandConsumer(engine, publisher, mapper);
    ReflectionTestUtils.setField(consumer, "simulationControl", control);
    consumer.setLiveProcessingEnabled(true);
    return consumer;
  }

  ConsumerRecord<String, String> record(OrderPlacedEvent event, long offset) throws Exception {
    return new ConsumerRecord<>(
        "order.commands", 0, offset, "BTC_USD", mapper.writeValueAsString(event));
  }

  @ParameterizedTest
  @ValueSource(strings = {"ADMISSION", "OUTPUTS", "PUBLISHED"})
  void pausedRestartCompletesOnlyTheDurableDecisionAndPreservesCanonicalOutputs(String crash)
      throws Exception {
    var context = control.requireActiveMutation();
    var maker =
        OrderPlacedEvent.of(
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instrument.BTC_USD,
                OrderSide.SELL,
                OrderType.LIMIT,
                100L,
                10L,
                UUID.randomUUID())
            .withSimulation(context);
    var taker =
        OrderPlacedEvent.of(
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instrument.BTC_USD,
                OrderSide.BUY,
                OrderType.IOC,
                100L,
                10L,
                UUID.randomUUID())
            .withSimulation(context);
    var makerRecord = record(maker, 0);
    var takerRecord = record(taker, 1);
    var publisher = mock(MatchingEventPublisher.class);
    var engine = new MatchingEngineService();
    try {
      var live = consumer(engine, publisher);
      live.onOrderCommand(makerRecord, () -> {});
      if (crash.equals("ADMISSION")) {
        control.decideMatching(
            "order.commands", 0, 1, context, SimulationControlRepository.hash(takerRecord.value()));
      } else {
        if (crash.equals("OUTPUTS"))
          doThrow(new IllegalStateException("broker unavailable"))
              .when(publisher)
              .publishStoredOutputs(anyList());
        assertThatThrownBy(
                () ->
                    live.onOrderCommand(
                        takerRecord,
                        () -> {
                          throw new IllegalStateException("crash before offset commit");
                        }))
            .isInstanceOf(IllegalStateException.class);
      }
    } finally {
      engine.shutdown();
    }
    var decision =
        control.replayAdmission(
            "order.commands", 0, 1, SimulationControlRepository.hash(takerRecord.value()));
    assertThat(decision.allowed()).isTrue();
    var original =
        jdbc.queryForList(
            "SELECT payload::text FROM simulator.matching_output WHERE record_offset=1 ORDER BY ordinal",
            String.class);
    control.beginStopping(context.sessionId(), context.fence(), "TEST_CRASH");
    var recovered = new MatchingEngineService();
    try {
      var restarted = consumer(recovered, mock(MatchingEventPublisher.class));
      var replay = new KafkaReplayService(recovered, restarted, mapper);
      ReflectionTestUtils.setField(replay, "simulationControl", control);
      var partition = new TopicPartition("order.commands", 0);
      try (var kafka = new MockConsumer<String, String>(OffsetResetStrategy.EARLIEST)) {
        kafka.updateBeginningOffsets(Map.of(partition, 0L));
        kafka.updateEndOffsets(Map.of(partition, 2L));
        kafka.schedulePollTask(
            () -> {
              kafka.addRecord(makerRecord);
              kafka.addRecord(takerRecord);
            });
        assertThat(replay.reconstruct(kafka, Map.of(partition, 1L))).isEqualTo(1);
      }
      var acknowledgments = new AtomicInteger();
      restarted.onOrderCommand(takerRecord, acknowledgments::incrementAndGet);
      assertThat(acknowledgments.get()).isEqualTo(1);
      var outputs =
          jdbc.queryForList(
              "SELECT payload::text FROM simulator.matching_output WHERE record_offset=1 ORDER BY ordinal",
              String.class);
      if (!original.isEmpty()) assertThat(outputs).isEqualTo(original);
      var tradeJson =
          jdbc.queryForObject(
              "SELECT payload::text FROM simulator.matching_output WHERE record_offset=1 AND event_topic='trade.executions'",
              String.class);
      var trade = mapper.readTree(tradeJson);
      assertThat(trade.path("simulation").path("elapsedMs").asLong())
          .isEqualTo(decision.context().elapsedMs());
      assertThat(trade.path("price").asLong()).isEqualTo(100L);
      assertThat(trade.path("quantity").asLong()).isEqualTo(10L);
      assertThat(recovered.getOrderBook(Instrument.BTC_USD).orderCount()).isZero();
      var late =
          OrderPlacedEvent.of(
                  UUID.randomUUID(),
                  UUID.randomUUID(),
                  Instrument.BTC_USD,
                  OrderSide.BUY,
                  OrderType.LIMIT,
                  100L,
                  10L,
                  UUID.randomUUID())
              .withSimulation(context);
      restarted.onOrderCommand(record(late, 2), acknowledgments::incrementAndGet);
      assertThat(
              jdbc.queryForObject(
                  "SELECT allowed FROM simulator.matching_admission WHERE record_offset=2",
                  Boolean.class))
          .isFalse();
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM simulator.matching_output WHERE event_topic='trade.executions'",
                  Integer.class))
          .isEqualTo(1);
      assertThat(recovered.getOrderBook(Instrument.BTC_USD).orderCount()).isZero();
    } finally {
      recovered.shutdown();
    }
  }
}

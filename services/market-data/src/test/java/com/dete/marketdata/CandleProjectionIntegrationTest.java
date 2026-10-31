package com.dete.marketdata;

import static org.assertj.core.api.Assertions.*;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.simulation.SimulationContext;
import com.dete.common.events.trade.TradeExecutedEvent;
import com.dete.marketdata.model.Interval;
import com.dete.marketdata.repository.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
class CandleProjectionIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  static JdbcTemplate jdbc;
  static TransactionTemplate tx;
  CandleRepository candles;
  MarketDataTradeRepository trades;
  UUID epoch;
  Instant t0 = Instant.parse("2026-10-30T12:34:27.123Z");

  @BeforeAll
  static void schema() {
    var ds =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    jdbc = new JdbcTemplate(ds);
    tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
    new ResourceDatabasePopulator(
            new ClassPathResource("db/migration/V1__create_market_data_schema.sql"),
            new ClassPathResource("db/migration/V2__simulation_trade_time_and_candles.sql"))
        .execute(ds);
    com.dete.common.test.SimulationTestSupport.initialize(jdbc);
  }

  @BeforeEach
  void setup() {
    jdbc.execute("TRUNCATE market_data.trades, market_data.candles");
    epoch = jdbc.queryForObject("SELECT epoch_id FROM simulator.market_clock", UUID.class);
    jdbc.update("UPDATE simulator.market_clock SET initialized_at=?", java.sql.Timestamp.from(t0));
    candles = new CandleRepository(jdbc);
    trades = new MarketDataTradeRepository(jdbc);
    ReflectionTestUtils.setField(trades, "candles", candles);
  }

  TradeExecutedEvent event(long elapsed, long sequence, long price, long qty) {
    var real = t0.plusSeconds(172800).plusMillis(elapsed);
    var context =
        new SimulationContext(
            epoch, UUID.randomUUID(), 1, t0, t0.plusMillis(elapsed), elapsed, real);
    return new TradeExecutedEvent(
        UUID.randomUUID(),
        UUID.randomUUID(),
        Instrument.ETH_USD,
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        price,
        qty,
        sequence,
        real,
        2,
        context);
  }

  void save(TradeExecutedEvent event) {
    tx.executeWithoutResult(s -> trades.saveTrade(event));
  }

  @Test
  void fiveActiveMinutesAreFiveAnchoredWindowsBeyondTapeCapacityAndRestart() {
    for (int i = 0; i < 600; i++) save(event(i * 500L, i + 1, 100 + i, 2));
    var restarted = new CandleRepository(jdbc);
    var result = restarted.history(epoch, Instrument.ETH_USD, Interval.ONE_MINUTE, null, 100);
    assertThat(result).hasSize(5);
    for (int i = 0; i < 5; i++) {
      var c = result.get(i);
      assertThat(c.openTime()).isEqualTo(t0.toEpochMilli() + i * 60000L);
      assertThat(c.open()).isEqualTo(100 + i * 120);
      assertThat(c.close()).isEqualTo(219 + i * 120);
      assertThat(c.volume()).isEqualTo(240);
      assertThat(c.tradeCount()).isEqualTo(120);
    }
    assertThat(
            restarted.history(
                epoch, Instrument.ETH_USD, Interval.ONE_MINUTE, result.get(3).openTime(), 2))
        .hasSize(2);
    assertThat(
            restarted
                .history(epoch, Instrument.ETH_USD, Interval.FIVE_MINUTES, null, 100)
                .getFirst()
                .volume())
        .isEqualTo(1200);
  }

  @Test
  void duplicatesAndLateTradesCorrectOpenCloseWithoutInflatingVolume() {
    var later = event(40000, 3, 120, 4);
    save(later);
    save(later);
    save(event(40000, 2, 110, 2));
    save(event(10000, 1, 90, 3));
    var c = candles.history(epoch, Instrument.ETH_USD, Interval.ONE_MINUTE, null, 100).getFirst();
    assertThat(c.open()).isEqualTo(90);
    assertThat(c.close()).isEqualTo(120);
    assertThat(c.high()).isEqualTo(120);
    assertThat(c.low()).isEqualTo(90);
    assertThat(c.volume()).isEqualTo(9);
    assertThat(c.tradeCount()).isEqualTo(3);
    assertThat(c.revision()).isEqualTo(3);
    save(event(50000, 4, 120, 1));
    assertThat(candles.history(epoch, Instrument.ETH_USD, Interval.ONE_MINUTE, null, 100))
        .hasSize(1);
    save(event(180000, 5, 120, 1));
    assertThat(candles.history(epoch, Instrument.ETH_USD, Interval.ONE_MINUTE, null, 100))
        .hasSize(2);
  }

  @Test
  void failedProjectionRollsBackTradeAndStaleEpochIsRejected() {
    var e = event(1000, 1, 100, 1);
    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    s -> {
                      trades.saveTrade(e);
                      throw new IllegalStateException("crash");
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM market_data.trades", Long.class)).isZero();
    assertThat(candles.history(epoch, Instrument.ETH_USD, Interval.ONE_MINUTE, null, 100))
        .isEmpty();
    epoch = UUID.randomUUID();
    assertThatThrownBy(() -> save(event(2000, 2, 100, 1)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

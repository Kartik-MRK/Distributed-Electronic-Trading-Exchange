package com.dete.marketdata.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dete.common.domain.enums.Instrument;
import com.dete.marketdata.model.Candle;
import com.dete.marketdata.model.Interval;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OHLCVManagerTest {

  private OHLCVManager ohlcvManager;

  @BeforeEach
  void setUp() {
    ohlcvManager = new OHLCVManager(100);
  }

  @Test
  @DisplayName("Should aggregate multiple trades within the same window across all 5 intervals")
  void testTradeAggregationWithinSameWindow() {
    Instant base = Instant.parse("2026-10-04T12:00:10Z");

    // First trade: price 50,000, qty 10
    Map<Interval, Candle> c1 = ohlcvManager.onTrade(Instrument.BTC_USD, 50_000L, 10L, base);
    assertThat(c1)
        .containsKeys(
            Interval.ONE_MINUTE,
            Interval.FIVE_MINUTES,
            Interval.FIFTEEN_MINUTES,
            Interval.ONE_HOUR,
            Interval.ONE_DAY);
    Candle min1 = c1.get(Interval.ONE_MINUTE);
    assertThat(min1.open()).isEqualTo(50_000L);
    assertThat(min1.high()).isEqualTo(50_000L);
    assertThat(min1.low()).isEqualTo(50_000L);
    assertThat(min1.close()).isEqualTo(50_000L);
    assertThat(min1.volume()).isEqualTo(10L);
    assertThat(min1.tradeCount()).isEqualTo(1);

    // Second trade: price 50,500, qty 5 (new high) at 12:00:20
    Instant t2 = base.plusSeconds(10);
    Map<Interval, Candle> c2 = ohlcvManager.onTrade(Instrument.BTC_USD, 50_500L, 5L, t2);
    min1 = c2.get(Interval.ONE_MINUTE);
    assertThat(min1.open()).isEqualTo(50_000L);
    assertThat(min1.high()).isEqualTo(50_500L);
    assertThat(min1.low()).isEqualTo(50_000L);
    assertThat(min1.close()).isEqualTo(50_500L);
    assertThat(min1.volume()).isEqualTo(15L);
    assertThat(min1.tradeCount()).isEqualTo(2);

    // Third trade: price 49,800, qty 20 (new low) at 12:00:40
    Instant t3 = base.plusSeconds(30);
    Map<Interval, Candle> c3 = ohlcvManager.onTrade(Instrument.BTC_USD, 49_800L, 20L, t3);
    min1 = c3.get(Interval.ONE_MINUTE);
    assertThat(min1.open()).isEqualTo(50_000L);
    assertThat(min1.high()).isEqualTo(50_500L);
    assertThat(min1.low()).isEqualTo(49_800L);
    assertThat(min1.close()).isEqualTo(49_800L);
    assertThat(min1.volume()).isEqualTo(35L);
    assertThat(min1.tradeCount()).isEqualTo(3);
  }

  @Test
  @DisplayName("Should roll over into a new candle when trade enters next interval window")
  void testCandleRollover() {
    Instant t1 = Instant.parse("2026-10-04T12:00:15Z");
    ohlcvManager.onTrade(Instrument.BTC_USD, 50_000L, 10L, t1);

    // Trade 70 seconds later (in next 1m candle window: 12:01:25)
    Instant t2 = Instant.parse("2026-10-04T12:01:25Z");
    ohlcvManager.onTrade(Instrument.BTC_USD, 51_000L, 15L, t2);

    List<Candle> oneMinCandles =
        ohlcvManager.getCandles(Instrument.BTC_USD, Interval.ONE_MINUTE, 10);
    assertThat(oneMinCandles).hasSize(2);

    // First candle in history
    Candle first = oneMinCandles.get(0);
    assertThat(first.open()).isEqualTo(50_000L);
    assertThat(first.close()).isEqualTo(50_000L);
    assertThat(first.volume()).isEqualTo(10L);

    // Second candle (current open)
    Candle second = oneMinCandles.get(1);
    assertThat(second.open()).isEqualTo(51_000L);
    assertThat(second.close()).isEqualTo(51_000L);
    assertThat(second.volume()).isEqualTo(15L);

    // In 5-minute interval, both trades should still be aggregated into 1 candle!
    List<Candle> fiveMinCandles =
        ohlcvManager.getCandles(Instrument.BTC_USD, Interval.FIVE_MINUTES, 10);
    assertThat(fiveMinCandles).hasSize(1);
    Candle fiveMin = fiveMinCandles.get(0);
    assertThat(fiveMin.open()).isEqualTo(50_000L);
    assertThat(fiveMin.high()).isEqualTo(51_000L);
    assertThat(fiveMin.close()).isEqualTo(51_000L);
    assertThat(fiveMin.volume()).isEqualTo(25L);
    assertThat(fiveMin.tradeCount()).isEqualTo(2);
  }
}

package com.dete.marketdata.repository;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.simulation.SimulationTime;
import com.dete.common.events.trade.TradeExecutedEvent;
import com.dete.marketdata.model.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Durable OHLCV projection. Called inside the same transaction as trade deduplication. */
@Repository
public class CandleRepository {
  private final JdbcTemplate jdbc;

  public CandleRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void validate(TradeExecutedEvent event) {
    var c = event.simulation();
    if (c == null
        || c.elapsedMs() < 0
        || !c.marketTime().equals(c.initializedAt().plusMillis(c.elapsedMs()))
        || event.price() <= 0
        || event.quantity() <= 0)
      throw new IllegalArgumentException("Execution requires valid simulation context");
    if (jdbc.queryForObject(
            "SELECT count(*) FROM simulator.market_clock WHERE epoch_id=? AND initialized_at=?",
            Long.class,
            c.epochId(),
            java.sql.Timestamp.from(c.initializedAt()))
        != 1) throw new IllegalArgumentException("Execution belongs to an obsolete epoch");
  }

  public void project(TradeExecutedEvent event) {
    var c = event.simulation();
    for (var interval : Interval.values()) {
      long open =
          SimulationTime.windowStart(c.initializedAt(), c.elapsedMs(), interval.getDurationMs())
              .toEpochMilli();
      jdbc.update(
          """
        INSERT INTO market_data.candles AS old
        (epoch_id,instrument,interval_code,open_time,close_time,open,high,low,close,volume,trade_count,first_elapsed,first_sequence,last_elapsed,last_sequence,revision)
        VALUES (?,?,?,?,?,?,?,?,?,?,1,?,?,?,?,1)
        ON CONFLICT(epoch_id,instrument,interval_code,open_time) DO UPDATE SET
          open=CASE WHEN (excluded.first_elapsed,excluded.first_sequence)<(old.first_elapsed,old.first_sequence) THEN excluded.open ELSE old.open END,
          close=CASE WHEN (excluded.last_elapsed,excluded.last_sequence)>(old.last_elapsed,old.last_sequence) THEN excluded.close ELSE old.close END,
          first_elapsed=LEAST(old.first_elapsed,excluded.first_elapsed),
          first_sequence=CASE WHEN (excluded.first_elapsed,excluded.first_sequence)<(old.first_elapsed,old.first_sequence) THEN excluded.first_sequence ELSE old.first_sequence END,
          last_elapsed=GREATEST(old.last_elapsed,excluded.last_elapsed),
          last_sequence=CASE WHEN (excluded.last_elapsed,excluded.last_sequence)>(old.last_elapsed,old.last_sequence) THEN excluded.last_sequence ELSE old.last_sequence END,
          high=GREATEST(old.high,excluded.high),low=LEAST(old.low,excluded.low),
          volume=old.volume+excluded.volume,trade_count=old.trade_count+1,revision=old.revision+1
        """,
          c.epochId(),
          event.instrument().name(),
          interval.getCode(),
          open,
          open + interval.getDurationMs(),
          event.price(),
          event.price(),
          event.price(),
          event.price(),
          event.quantity(),
          c.elapsedMs(),
          event.sequenceNumber(),
          c.elapsedMs(),
          event.sequenceNumber());
    }
    jdbc.update(
        """
      INSERT INTO simulator.market_resume_price(epoch_id,instrument,price,sequence_number) VALUES (?,?,?,?)
      ON CONFLICT(epoch_id,instrument) DO UPDATE SET price=excluded.price,sequence_number=excluded.sequence_number
      WHERE excluded.sequence_number>market_resume_price.sequence_number
      """,
        c.epochId(),
        event.instrument().name(),
        event.price(),
        event.sequenceNumber());
  }

  public UUID currentEpoch() {
    var epochs =
        jdbc.query(
            "SELECT epoch_id FROM simulator.market_clock", (rs, n) -> rs.getObject(1, UUID.class));
    return epochs.isEmpty() ? null : epochs.getFirst();
  }

  public List<Candle> history(
      UUID epoch, Instrument instrument, Interval interval, Long before, int limit) {
    if (epoch == null) return List.of();
    var result =
        new ArrayList<>(
            jdbc.query(
                """
      SELECT * FROM market_data.candles WHERE epoch_id=? AND instrument=? AND interval_code=? AND open_time<? ORDER BY open_time DESC LIMIT ?
      """,
                (rs, n) ->
                    new Candle(
                        rs.getLong("open_time"),
                        rs.getLong("close_time"),
                        rs.getLong("open"),
                        rs.getLong("high"),
                        rs.getLong("low"),
                        rs.getLong("close"),
                        rs.getLong("volume"),
                        rs.getInt("trade_count"),
                        rs.getObject("epoch_id", UUID.class),
                        rs.getLong("revision"),
                        false),
                epoch,
                instrument.name(),
                interval.getCode(),
                before == null ? Long.MAX_VALUE : before,
                Math.max(1, Math.min(1000, limit))));
    Collections.reverse(result);
    return result;
  }

  public Map<Interval, Candle> forTrade(TradeExecutedEvent trade) {
    var result = new EnumMap<Interval, Candle>(Interval.class);
    for (var interval : Interval.values()) {
      long open =
          SimulationTime.windowStart(
                  trade.simulation().initializedAt(),
                  trade.simulation().elapsedMs(),
                  interval.getDurationMs())
              .toEpochMilli();
      var rows = history(trade.simulation().epochId(), trade.instrument(), interval, open + 1, 1);
      if (!rows.isEmpty()) result.put(interval, rows.getFirst());
    }
    return result;
  }
}

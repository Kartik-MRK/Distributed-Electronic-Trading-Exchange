package com.dete.marketdata.repository;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.events.trade.TradeExecutedEvent;
import com.dete.marketdata.model.MarketDataTradeRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Repository for persisting and retrieving historical trade execution records from
 * market_data.trades. Read-model used for trade queries, historical audits, and Phase 12 Replay
 * Viewer.
 */
@Repository
public class MarketDataTradeRepository {

  private static final Logger log = LoggerFactory.getLogger(MarketDataTradeRepository.class);

  private final JdbcTemplate jdbcTemplate;

  public MarketDataTradeRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  private static final String INSERT_TRADE_SQL =
      """
      INSERT INTO market_data.trades (
          trade_id, instrument, sequence_number, price, quantity,
          buy_order_id, sell_order_id, buy_account_id, sell_account_id, executed_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT (trade_id) DO NOTHING
      """;

  private static final String SELECT_BY_INSTRUMENT_SQL =
      """
      SELECT trade_id, instrument, sequence_number, price, quantity,
             buy_order_id, sell_order_id, buy_account_id, sell_account_id, executed_at
      FROM market_data.trades
      WHERE instrument = ?
      ORDER BY sequence_number DESC
      LIMIT ?
      """;

  private static final String SELECT_BY_WINDOW_SQL =
      """
      SELECT trade_id, instrument, sequence_number, price, quantity,
             buy_order_id, sell_order_id, buy_account_id, sell_account_id, executed_at
      FROM market_data.trades
      WHERE instrument = ? AND executed_at >= ? AND executed_at <= ?
      ORDER BY executed_at DESC
      LIMIT ?
      """;

  private static final String SELECT_BY_SEQ_RANGE_SQL =
      """
      SELECT trade_id, instrument, sequence_number, price, quantity,
             buy_order_id, sell_order_id, buy_account_id, sell_account_id, executed_at
      FROM market_data.trades
      WHERE instrument = ? AND sequence_number >= ? AND sequence_number <= ?
      ORDER BY sequence_number ASC
      """;

  public void saveTrade(TradeExecutedEvent trade) {
    if (trade == null) return;
    try {
      jdbcTemplate.update(
          INSERT_TRADE_SQL,
          trade.tradeId(),
          trade.instrument().name(),
          trade.sequenceNumber(),
          trade.price(),
          trade.quantity(),
          trade.buyOrderId(),
          trade.sellOrderId(),
          trade.buyAccountId(),
          trade.sellAccountId(),
          Timestamp.from(trade.timestamp()));
    } catch (Exception e) {
      log.error("Failed to persist trade {}", trade.tradeId(), e);
      throw e;
    }
  }

  public List<MarketDataTradeRecord> findByInstrument(Instrument instrument, int limit) {
    int effectiveLimit = limit <= 0 ? 100 : limit;
    return jdbcTemplate.query(
        SELECT_BY_INSTRUMENT_SQL, new TradeRowMapper(), instrument.name(), effectiveLimit);
  }

  public List<MarketDataTradeRecord> findByInstrumentAndWindow(
      Instrument instrument, Instant from, Instant to, int limit) {
    int effectiveLimit = limit <= 0 ? 100 : limit;
    return jdbcTemplate.query(
        SELECT_BY_WINDOW_SQL,
        new TradeRowMapper(),
        instrument.name(),
        Timestamp.from(from),
        Timestamp.from(to),
        effectiveLimit);
  }

  public List<MarketDataTradeRecord> findByInstrumentAndSequenceRange(
      Instrument instrument, long fromSeq, long toSeq) {
    return jdbcTemplate.query(
        SELECT_BY_SEQ_RANGE_SQL, new TradeRowMapper(), instrument.name(), fromSeq, toSeq);
  }

  private static class TradeRowMapper implements RowMapper<MarketDataTradeRecord> {
    @Override
    public MarketDataTradeRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
      return new MarketDataTradeRecord(
          rs.getObject("trade_id", UUID.class),
          Instrument.valueOf(rs.getString("instrument")),
          rs.getLong("sequence_number"),
          rs.getLong("price"),
          rs.getLong("quantity"),
          rs.getObject("buy_order_id", UUID.class),
          rs.getObject("sell_order_id", UUID.class),
          rs.getObject("buy_account_id", UUID.class),
          rs.getObject("sell_account_id", UUID.class),
          rs.getTimestamp("executed_at").toInstant());
    }
  }
}

package com.dete.marketdata.service;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.events.trade.TradeExecutedEvent;
import com.dete.marketdata.model.Candle;
import com.dete.marketdata.model.Interval;
import com.dete.marketdata.model.OrderBookSnapshotResponse;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/** Dispatches real-time market data updates and private order events over WebSocket / STOMP. */
@Service
public class MarketDataWebSocketBroadcaster {

  private static final Logger log = LoggerFactory.getLogger(MarketDataWebSocketBroadcaster.class);

  private final SimpMessagingTemplate messagingTemplate;

  public MarketDataWebSocketBroadcaster(SimpMessagingTemplate messagingTemplate) {
    this.messagingTemplate = messagingTemplate;
  }

  public void broadcastOrderBook(Instrument instrument, OrderBookSnapshotResponse snapshot) {
    String topicSymbol = "/topic/orderbook." + instrument.symbol();
    messagingTemplate.convertAndSend(topicSymbol, snapshot);

    String topicName = "/topic/orderbook." + instrument.name();
    messagingTemplate.convertAndSend(topicName, snapshot);
    log.trace("Broadcast order book to {} and {}", topicSymbol, topicName);
  }

  public void broadcastTrade(Instrument instrument, TradeExecutedEvent trade) {
    String topicSymbol = "/topic/trades." + instrument.symbol();
    messagingTemplate.convertAndSend(topicSymbol, trade);

    String topicName = "/topic/trades." + instrument.name();
    messagingTemplate.convertAndSend(topicName, trade);
    log.trace("Broadcast trade to {} and {}", topicSymbol, topicName);
  }

  public void broadcastCandle(Instrument instrument, Interval interval, Candle candle) {
    String topicSymbol = "/topic/candles." + instrument.symbol() + "." + interval.getCode();
    messagingTemplate.convertAndSend(topicSymbol, candle);

    String topicName = "/topic/candles." + instrument.name() + "." + interval.getCode();
    messagingTemplate.convertAndSend(topicName, candle);
    log.trace("Broadcast candle to {} and {}", topicSymbol, topicName);
  }

  public void broadcastUserOrderUpdate(UUID accountId, Object orderUpdate) {
    if (accountId == null || orderUpdate == null) return;
    String topic = "/topic/orders." + accountId;
    messagingTemplate.convertAndSend(topic, orderUpdate);
    log.trace("Broadcast private order update to {}", topic);
  }
}

package com.dete.marketdata.config;

import com.dete.marketdata.metrics.MarketDataMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class WebSocketEventListener {

  private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);

  private final MarketDataMetrics marketDataMetrics;

  public WebSocketEventListener(MarketDataMetrics marketDataMetrics) {
    this.marketDataMetrics = marketDataMetrics;
  }

  @EventListener
  public void handleWebSocketConnectListener(SessionConnectedEvent event) {
    marketDataMetrics.connectionEstablished();
    log.info(
        "New WebSocket connection established. Active: {}",
        marketDataMetrics.getActiveConnections());
  }

  @EventListener
  public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
    marketDataMetrics.connectionClosed();
    log.info("WebSocket connection closed. Active: {}", marketDataMetrics.getActiveConnections());
  }
}

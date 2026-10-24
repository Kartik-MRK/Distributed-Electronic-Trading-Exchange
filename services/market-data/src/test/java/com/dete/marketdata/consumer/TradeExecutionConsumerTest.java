package com.dete.marketdata.consumer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.dete.marketdata.service.MarketDataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

class TradeExecutionConsumerTest {
  @Test
  void malformedExecutionIsNotAcknowledged() {
    var ack = mock(Acknowledgment.class);
    var consumer = new TradeExecutionConsumer(mock(MarketDataService.class), new ObjectMapper());
    assertThatThrownBy(
            () ->
                consumer.onTradeExecuted(
                    new ConsumerRecord<>("trade.executions", 0, 1, "BTC_USD", "invalid"), ack))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(ack);
  }
}

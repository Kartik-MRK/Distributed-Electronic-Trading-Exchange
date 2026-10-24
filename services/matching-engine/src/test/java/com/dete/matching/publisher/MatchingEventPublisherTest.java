package com.dete.matching.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.domain.enums.OrderType;
import com.dete.common.events.order.OrderPlacedEvent;
import com.dete.matching.engine.OrderBook;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

class MatchingEventPublisherTest {
  @Test
  void publishesPartialFillsBeforeFinalFillForMultiMakerSweep() throws Exception {
    KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    when(kafka.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(null));
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    OrderBook book = new OrderBook(Instrument.BTC_USD);
    UUID maker = UUID.randomUUID();
    for (int i = 0; i < 2; i++) {
      book.processOrder(
          OrderPlacedEvent.of(
              UUID.randomUUID(),
              maker,
              Instrument.BTC_USD,
              OrderSide.SELL,
              OrderType.LIMIT,
              100L,
              10L,
              UUID.randomUUID()));
    }
    UUID taker = UUID.randomUUID();
    var result =
        book.processOrder(
            OrderPlacedEvent.of(
                taker,
                UUID.randomUUID(),
                Instrument.BTC_USD,
                OrderSide.BUY,
                OrderType.LIMIT,
                100L,
                20L,
                UUID.randomUUID()));
    new MatchingEventPublisher(kafka, mapper).publishMatchResult(result);
    ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
    verify(kafka, org.mockito.Mockito.times(3))
        .send(eq("order.events"), eq(taker.toString()), payload.capture());
    var events = payload.getAllValues();
    assertThat(mapper.readTree(events.get(1)).get("remainingQuantity").asLong()).isEqualTo(10L);
    assertThat(mapper.readTree(events.get(2)).has("fillQuantity")).isTrue();
    assertThat(mapper.readTree(events.get(2)).has("remainingQuantity")).isFalse();
  }
}

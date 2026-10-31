package com.dete.order.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.dete.common.domain.enums.*;
import com.dete.order.client.*;
import com.dete.order.dto.CreateOrderRequest;
import com.dete.order.repository.*;
import com.dete.simulation.control.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OrderSimulationGateTest {
  @Test
  void pausedOrderCannotReserveFundsOrEnterRisk() {
    var orders = mock(OrderRepository.class);
    var outbox = mock(OrderOutboxRepository.class);
    var account = mock(AccountClient.class);
    var risk = mock(PreTradeRiskValidator.class);
    var idem = mock(IdempotencyService.class);
    var control = mock(SimulationControlRepository.class);
    var service = new OrderService(orders, outbox, account, risk, idem, new ObjectMapper());
    ReflectionTestUtils.setField(service, "simulationControl", control);
    when(control.requireActiveMutation()).thenThrow(new SimulationClosedException("Paused"));
    var request =
        new CreateOrderRequest(
            Instrument.BTC_USD, OrderSide.BUY, OrderType.LIMIT, 6000000000000L, 100000L);
    assertThatThrownBy(() -> service.placeOrder(request, UUID.randomUUID(), UUID.randomUUID()))
        .isInstanceOf(SimulationClosedException.class);
    verifyNoInteractions(account, risk, outbox);
    verify(orders, never()).save(any());
  }

  @Test
  void delayedRequestCannotEnterAnotherSession() throws Exception {
    var orders = mock(OrderRepository.class);
    var outbox = mock(OrderOutboxRepository.class);
    var account = mock(AccountClient.class);
    var risk = mock(PreTradeRiskValidator.class);
    var control = mock(SimulationControlRepository.class);
    var service =
        new OrderService(
            orders, outbox, account, risk, mock(IdempotencyService.class), new ObjectMapper());
    ReflectionTestUtils.setField(service, "simulationControl", control);
    var now = java.time.Instant.now();
    when(control.requireActiveMutation())
        .thenReturn(
            new com.dete.common.domain.simulation.SimulationContext(
                UUID.randomUUID(), UUID.randomUUID(), 2, now, now, 0, now));
    var mapper = new ObjectMapper();
    var request =
        mapper.readValue(
            "{\"instrument\":\"BTC_USD\",\"side\":\"BUY\",\"orderType\":\"LIMIT\",\"price\":6000000000000,\"quantity\":100000,\"sessionId\":\""
                + UUID.randomUUID()
                + "\"}",
            CreateOrderRequest.class);
    assertThatThrownBy(() -> service.placeOrder(request, UUID.randomUUID(), UUID.randomUUID()))
        .isInstanceOf(SimulationClosedException.class);
    verifyNoInteractions(account, risk, outbox);
    verify(orders, never()).save(any());
  }
}

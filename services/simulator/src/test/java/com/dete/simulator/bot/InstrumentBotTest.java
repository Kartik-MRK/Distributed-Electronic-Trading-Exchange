package com.dete.simulator.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dete.common.domain.enums.OrderStatus;
import com.dete.simulator.client.ExchangeRestClient;
import com.dete.simulator.config.SimulatorProperties;
import com.dete.simulator.dto.CreateOrderDto;
import com.dete.simulator.dto.OrderResponseDto;
import com.dete.simulator.service.BotAccountManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InstrumentBotTest {

  private ExchangeRestClient restClient;
  private BotAccountManager accountManager;
  private SimulatorProperties.InstrumentConfig config;
  private InstrumentBot bot;

  @BeforeEach
  void setUp() {
    restClient = mock(ExchangeRestClient.class);
    accountManager = mock(BotAccountManager.class);

    when(accountManager.getMakerToken()).thenReturn("mock-maker-token");
    when(accountManager.getTakerToken()).thenReturn("mock-taker-token");

    when(restClient.placeOrder(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              CreateOrderDto req = invocation.getArgument(1);
              return new OrderResponseDto(
                  UUID.randomUUID(),
                  UUID.randomUUID(),
                  req.instrument(),
                  req.side(),
                  req.orderType(),
                  OrderStatus.ACCEPTED,
                  req.price(),
                  req.quantity(),
                  0L,
                  req.quantity());
            });

    config = new SimulatorProperties.InstrumentConfig("BTC-USD", 65000.0, 20, 5, 0.01, 0.1);
    bot = new InstrumentBot(config, restClient, accountManager);
  }

  @Test
  @DisplayName("Should populate order book with configured number of bids and asks")
  void shouldPopulateOrderBook() {
    bot.populateOrderBook();

    assertThat(bot.getActiveBidCount()).isEqualTo(5);
    assertThat(bot.getActiveAskCount()).isEqualTo(5);
    verify(restClient, atLeastOnce())
        .placeOrder(eq("mock-maker-token"), any(CreateOrderDto.class), any());
  }

  @Test
  @DisplayName("Should drift mid price within expected delta range")
  void shouldDriftMidPrice() {
    double initialMid = bot.getCurrentMidPrice();

    for (int i = 0; i < 10; i++) {
      bot.driftMidPrice();
    }

    double currentMid = bot.getCurrentMidPrice();
    // After 10 cycles of ±0.3%, price should stay within ±5%
    assertThat(currentMid).isBetween(initialMid * 0.95, initialMid * 1.05);
  }

  @Test
  @DisplayName("Should generate crossing fill from taker bot")
  void shouldGenerateFill() {
    bot.populateOrderBook();
    bot.generateFill();

    verify(restClient, atLeastOnce())
        .placeOrder(eq("mock-taker-token"), any(CreateOrderDto.class), any());
  }

  @Test
  @DisplayName("Should cancel subset of orders and refresh depth")
  void shouldRefreshOrders() {
    bot.populateOrderBook();
    bot.refreshOrders();

    // After refresh, the bot cancels 2 and replenishes back up to config.levels
    assertThat(bot.getActiveBidCount()).isEqualTo(5);
    assertThat(bot.getActiveAskCount()).isEqualTo(5);
  }
}

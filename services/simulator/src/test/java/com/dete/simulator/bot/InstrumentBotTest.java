package com.dete.simulator.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderStatus;
import com.dete.common.domain.types.FixedPoint;
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

    when(restClient.getBestQuotes(any()))
        .thenReturn(
            new ExchangeRestClient.BestQuotes(
                64000L * FixedPoint.SCALE, 66000L * FixedPoint.SCALE));
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
  @DisplayName("Should populate Ethereum and Solana order books")
  void shouldPopulateEthAndSolOrderBooks() {
    for (var market :
        java.util.List.of(new Object[] {"ETH-USD", 3500.0}, new Object[] {"SOL-USD", 150.0})) {
      String symbol = (String) market[0];
      double initialMid = (double) market[1];
      SimulatorProperties.InstrumentConfig marketConfig =
          new SimulatorProperties.InstrumentConfig(symbol, initialMid, 20, 5, 0.01, 0.1);
      InstrumentBot marketBot = new InstrumentBot(marketConfig, restClient, accountManager);

      marketBot.populateOrderBook();

      assertThat(marketBot.getInstrument()).isEqualTo(Instrument.fromSymbol(symbol));
      assertThat(marketBot.getActiveBidCount()).isEqualTo(5);
      assertThat(marketBot.getActiveAskCount()).isEqualTo(5);
      assertThat(marketBot.getCurrentMidPrice()).isEqualTo(initialMid);
    }
  }

  @Test
  @DisplayName("Should align ETH quotes to the latest traded price")
  void shouldAlignQuotesToLatestTradedPrice() {
    var ethConfig = new SimulatorProperties.InstrumentConfig("ETH-USD", 3500.0, 20, 6, 0.01, 0.5);
    var ethBot = new InstrumentBot(ethConfig, restClient, accountManager);
    ethBot.setCurrentMidPrice(3_000L * FixedPoint.SCALE);

    ethBot.populateOrderBook();

    assertThat(ethBot.getCurrentMidPrice()).isEqualTo(3000.0);
    assertThat(ethBot.getActiveBidCount()).isEqualTo(6);
    assertThat(ethBot.getActiveAskCount()).isEqualTo(6);
  }

  @Test
  @DisplayName("Should drift mid price within expected delta range")
  void shouldDriftMidPrice() {
    double initialMid = bot.getCurrentMidPrice();

    for (int i = 0; i < 10; i++) {
      bot.driftMidPrice();
    }

    double currentMid = bot.getCurrentMidPrice();
    // Drift remains inside the risk service's 10% band with room for quote spread.
    assertThat(currentMid).isBetween(initialMid * 0.92, initialMid * 1.08);
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

    // Empty authoritative book removes consumed orders; one pair is replenished per turn.
    assertThat(bot.getActiveBidCount()).isEqualTo(1);
    assertThat(bot.getActiveAskCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("A worker that lost its session fence cannot place bot orders")
  void shouldNotSubmitWhenSessionOwnershipIsLost() {
    InstrumentBot staleBot = new InstrumentBot(config, restClient, accountManager, () -> false);

    staleBot.populateOrderBook();

    org.mockito.Mockito.verifyNoInteractions(restClient);
  }

  @Test
  void takerCrossesActualQuoteAndCarriesItsSession() {
    var session = UUID.randomUUID();
    bot.bindSession(session);
    when(restClient.getBestQuotes(Instrument.BTC_USD))
        .thenReturn(
            new ExchangeRestClient.BestQuotes(
                64000L * FixedPoint.SCALE, 66000L * FixedPoint.SCALE));
    var requests = new java.util.ArrayList<CreateOrderDto>();
    org.mockito.Mockito.doAnswer(
            inv -> {
              requests.add(inv.getArgument(1));
              return null;
            })
        .when(restClient)
        .placeOrder(eq("mock-taker-token"), any(), any());
    for (int i = 0; i < 20; i++) bot.generateFill();
    assertThat(requests).hasSize(20);
    for (var request : requests) {
      assertThat(request.sessionId()).isEqualTo(session);
      assertThat(request.price())
          .isEqualTo(
              (request.side() == com.dete.common.domain.enums.OrderSide.BUY ? 66000L : 64000L)
                  * FixedPoint.SCALE);
      assertThat(request.orderType()).isEqualTo(com.dete.common.domain.enums.OrderType.IOC);
    }
  }

  @Test
  void reconciliationRemovesConsumedOrdersAndRefreshIsBounded() {
    bot.populateOrderBook();
    when(restClient.getActiveOrdersStrict("mock-maker-token")).thenReturn(java.util.List.of());
    bot.refreshOrders();
    assertThat(bot.getActiveBidCount()).isEqualTo(1);
    assertThat(bot.getActiveAskCount()).isEqualTo(1);
  }

  @Test
  void pendingCancellationsDoNotPermitPrematureReplacement() {
    var active = new java.util.ArrayList<OrderResponseDto>();
    org.mockito.Mockito.doAnswer(
            inv -> {
              CreateOrderDto req = inv.getArgument(1);
              var response =
                  new OrderResponseDto(
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      req.instrument(),
                      req.side(),
                      req.orderType(),
                      OrderStatus.ACCEPTED,
                      req.price(),
                      req.quantity(),
                      0,
                      req.quantity());
              active.add(response);
              return response;
            })
        .when(restClient)
        .placeOrder(any(), any(), any());
    bot.populateOrderBook();
    when(restClient.getActiveOrdersStrict("mock-maker-token")).thenReturn(active);
    bot.refreshOrders();
    assertThat(active).hasSize(10);
    assertThat(bot.getActiveBidCount()).isEqualTo(5);
    assertThat(bot.getActiveAskCount()).isEqualTo(5);
  }
}

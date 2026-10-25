package com.dete.simulator.bot;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.domain.enums.OrderType;
import com.dete.common.domain.types.FixedPoint;
import com.dete.simulator.client.ExchangeRestClient;
import com.dete.simulator.config.SimulatorProperties;
import com.dete.simulator.dto.CreateOrderDto;
import com.dete.simulator.dto.OrderResponseDto;
import com.dete.simulator.service.BotAccountManager;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InstrumentBot {

  private static final Logger log = LoggerFactory.getLogger(InstrumentBot.class);

  private final Instrument instrument;
  private final SimulatorProperties.InstrumentConfig config;
  private final ExchangeRestClient restClient;
  private final BotAccountManager accountManager;
  private final BooleanSupplier sessionIsCurrent;
  private final BooleanSupplier orderAdmission;
  private final MeterRegistry meters;
  private final Random random = new Random();

  private volatile double currentMidPrice;
  private final Set<UUID> restingBidOrderIds = ConcurrentHashMap.newKeySet();
  private final Set<UUID> restingAskOrderIds = ConcurrentHashMap.newKeySet();
  private final Set<UUID> restingTakerOrderIds = ConcurrentHashMap.newKeySet();

  public InstrumentBot(
      SimulatorProperties.InstrumentConfig config,
      ExchangeRestClient restClient,
      BotAccountManager accountManager) {
    this(config, restClient, accountManager, () -> true, () -> true, new SimpleMeterRegistry());
  }

  public InstrumentBot(
      SimulatorProperties.InstrumentConfig config,
      ExchangeRestClient restClient,
      BotAccountManager accountManager,
      BooleanSupplier sessionIsCurrent) {
    this(
        config,
        restClient,
        accountManager,
        sessionIsCurrent,
        () -> true,
        new SimpleMeterRegistry());
  }

  public InstrumentBot(
      SimulatorProperties.InstrumentConfig config,
      ExchangeRestClient restClient,
      BotAccountManager accountManager,
      BooleanSupplier sessionIsCurrent,
      MeterRegistry meters) {
    this(config, restClient, accountManager, sessionIsCurrent, () -> true, meters);
  }

  public InstrumentBot(
      SimulatorProperties.InstrumentConfig config,
      ExchangeRestClient restClient,
      BotAccountManager accountManager,
      BooleanSupplier sessionIsCurrent,
      BooleanSupplier orderAdmission,
      MeterRegistry meters) {
    this.config = config;
    this.instrument = Instrument.fromSymbol(config.getSymbol());
    this.restClient = restClient;
    this.accountManager = accountManager;
    this.sessionIsCurrent = sessionIsCurrent;
    this.orderAdmission = orderAdmission;
    this.meters = meters;
    this.currentMidPrice = config.getInitialMid();
  }

  public Instrument getInstrument() {
    return instrument;
  }

  public double getCurrentMidPrice() {
    return currentMidPrice;
  }

  public int getActiveBidCount() {
    return restingBidOrderIds.size();
  }

  public int getActiveAskCount() {
    return restingAskOrderIds.size();
  }

  /** Initializes order book depth with N levels of bids and asks. */
  public void populateOrderBook() {
    String makerToken = accountManager.getMakerToken();
    if (makerToken == null) {
      log.warn("Cannot populate order book for {}: Maker token unavailable", instrument.symbol());
      return;
    }

    double halfSpreadPercent = (config.getSpreadBps() / 10000.0) / 2.0;
    double bestBid = currentMidPrice * (1.0 - halfSpreadPercent);
    double bestAsk = currentMidPrice * (1.0 + halfSpreadPercent);

    int levels = targetLevels();
    for (int i = 0; i < levels; i++) {
      double stepPercent = 0.02 * ((double) i / (double) levels);
      double bidPrice = bestBid * (1.0 - stepPercent);
      double askPrice = bestAsk * (1.0 + stepPercent);

      placeMakerOrder(makerToken, OrderSide.BUY, bidPrice, restingBidOrderIds);
      placeMakerOrder(makerToken, OrderSide.SELL, askPrice, restingAskOrderIds);
    }

    log.info(
        "Populated order book for {} with {} bids and {} asks around mid {}",
        instrument.symbol(),
        restingBidOrderIds.size(),
        restingAskOrderIds.size(),
        String.format("%.2f", currentMidPrice));
  }

  /** Drifts mid-price randomly within [-0.3%, +0.3%] every cycle. */
  public void driftMidPrice() {
    double delta = (random.nextDouble() * 0.006) - 0.003; // [-0.3%, +0.3%]

    // Gentle mean reversion if price wanders too far from initial mid (±15%)
    if (currentMidPrice > config.getInitialMid() * 1.15) {
      delta -= 0.001;
    } else if (currentMidPrice < config.getInitialMid() * 0.85) {
      delta += 0.001;
    }

    currentMidPrice = currentMidPrice * (1.0 + delta);
    log.debug(
        "Drifted mid-price for {} to {} (delta: {}%)",
        instrument.symbol(),
        String.format("%.2f", currentMidPrice),
        String.format("%.3f", delta * 100));
  }

  /** Refreshes resting orders, cancels stale quotes, and replenishes depth at the new mid price. */
  public void refreshOrders() {
    String makerToken = accountManager.getMakerToken();
    if (makerToken == null) return;
    refreshTakerOrderCount();

    // Cancel 2 random bids and 2 random asks
    cancelRandomSubset(makerToken, restingBidOrderIds, 2);
    cancelRandomSubset(makerToken, restingAskOrderIds, 2);

    // Replenish quotes to maintain configured levels
    double halfSpreadPercent = (config.getSpreadBps() / 10000.0) / 2.0;
    double bestBid = currentMidPrice * (1.0 - halfSpreadPercent);
    double bestAsk = currentMidPrice * (1.0 + halfSpreadPercent);

    int attempts = 0;
    int maxAttempts = targetLevels();
    while (restingBidOrderIds.size() < targetLevels()
        && attempts++ < maxAttempts
        && sessionIsCurrent.getAsBoolean()) {
      double step = 0.02 * random.nextDouble();
      placeMakerOrder(makerToken, OrderSide.BUY, bestBid * (1.0 - step), restingBidOrderIds);
    }

    attempts = 0;
    while (restingAskOrderIds.size() < targetLevels()
        && attempts++ < maxAttempts
        && sessionIsCurrent.getAsBoolean()) {
      double step = 0.02 * random.nextDouble();
      placeMakerOrder(makerToken, OrderSide.SELL, bestAsk * (1.0 + step), restingAskOrderIds);
    }
  }

  /** Places a bounded crossing limit order; fills still occur only in the exchange matcher. */
  public void generateFill() {
    if (!sessionIsCurrent.getAsBoolean()) return;
    if (restingTakerOrderIds.size() >= targetLevels()) return;
    if (!orderAdmission.getAsBoolean() || !sessionIsCurrent.getAsBoolean()) return;
    String takerToken = accountManager.getTakerToken();
    if (takerToken == null) return;

    OrderSide side = random.nextBoolean() ? OrderSide.BUY : OrderSide.SELL;
    double sizeRange = config.getOrderSizeMax() - config.getOrderSizeMin();
    double quantity =
        config.getOrderSizeMin() + (random.nextDouble() * Math.max(0.0001, sizeRange * 0.3));
    long quantityNanos = Math.round(quantity * FixedPoint.SCALE);

    double halfSpreadPercent = (config.getSpreadBps() / 10000.0) / 2.0;
    // Crossing price to ensure immediate execution with price improvement
    double crossingPrice =
        (side == OrderSide.BUY)
            ? currentMidPrice * (1.0 + halfSpreadPercent * 2.0)
            : currentMidPrice * (1.0 - halfSpreadPercent * 2.0);
    long priceNanos = Math.round(crossingPrice * FixedPoint.SCALE);

    CreateOrderDto orderRequest =
        new CreateOrderDto(instrument, side, OrderType.LIMIT, priceNanos, quantityNanos);

    OrderResponseDto response = restClient.placeOrder(takerToken, orderRequest, UUID.randomUUID());
    if (response != null) {
      orderCounter("taker").increment();
      if (response.status() == com.dete.common.domain.enums.OrderStatus.FILLED
          || response.status() == com.dete.common.domain.enums.OrderStatus.PARTIALLY_FILLED) {
        meters
            .counter(
                "market.simulation.bot.fills",
                "market",
                instrument.symbol(),
                "status",
                response.status().name())
            .increment();
      }
      if (response.orderId() != null
          && response.status() != com.dete.common.domain.enums.OrderStatus.FILLED
          && response.status() != com.dete.common.domain.enums.OrderStatus.CANCELLED
          && response.status() != com.dete.common.domain.enums.OrderStatus.REJECTED) {
        restingTakerOrderIds.add(response.orderId());
      }
      log.debug(
          "Submitted simulated taker order: {} {} {} @ {}",
          side,
          String.format("%.4f", quantity),
          instrument.symbol(),
          String.format("%.2f", crossingPrice));
    }
  }

  private void placeMakerOrder(String token, OrderSide side, double price, Set<UUID> idSet) {
    if (!sessionIsCurrent.getAsBoolean()
        || !orderAdmission.getAsBoolean()
        || !sessionIsCurrent.getAsBoolean()) return;
    double sizeRange = config.getOrderSizeMax() - config.getOrderSizeMin();
    double quantity = config.getOrderSizeMin() + (random.nextDouble() * sizeRange);

    long priceNanos = Math.round(price * FixedPoint.SCALE);
    long quantityNanos = Math.round(quantity * FixedPoint.SCALE);

    CreateOrderDto request =
        new CreateOrderDto(instrument, side, OrderType.LIMIT, priceNanos, quantityNanos);

    OrderResponseDto response = restClient.placeOrder(token, request, UUID.randomUUID());
    if (response != null && response.orderId() != null) {
      idSet.add(response.orderId());
      orderCounter("maker").increment();
    }
  }

  private io.micrometer.core.instrument.Counter orderCounter(String role) {
    return meters.counter(
        "market.simulation.bot.orders", "market", instrument.symbol(), "role", role);
  }

  private int targetLevels() {
    return Math.max(3, Math.min(15, config.getLevels()));
  }

  private void refreshTakerOrderCount() {
    String takerToken = accountManager.getTakerToken();
    if (takerToken == null) return;
    Set<UUID> activeIds = ConcurrentHashMap.newKeySet();
    for (OrderResponseDto order : restClient.getActiveOrdersStrict(takerToken)) {
      if (instrument.equals(order.instrument())
          && order.orderId() != null
          && order.status() != com.dete.common.domain.enums.OrderStatus.FILLED
          && order.status() != com.dete.common.domain.enums.OrderStatus.CANCELLED
          && order.status() != com.dete.common.domain.enums.OrderStatus.REJECTED) {
        activeIds.add(order.orderId());
      }
    }
    restingTakerOrderIds.clear();
    restingTakerOrderIds.addAll(activeIds);
  }

  private void cancelRandomSubset(String token, Set<UUID> idSet, int count) {
    if (idSet.isEmpty()) return;
    List<UUID> list = new ArrayList<>(idSet);
    int toCancel = Math.min(count, list.size());
    for (int i = 0; i < toCancel; i++) {
      if (!sessionIsCurrent.getAsBoolean()) return;
      int index = random.nextInt(list.size());
      UUID orderId = list.remove(index);
      idSet.remove(orderId);
      restClient.cancelOrder(token, orderId);
    }
  }
}

package com.dete.matching.engine.model;

import com.dete.common.events.DeteEvent;
import com.dete.common.events.order.OrderAcceptedEvent;
import com.dete.common.events.order.OrderCancelledEvent;
import com.dete.common.events.order.OrderFilledEvent;
import com.dete.common.events.order.OrderPartiallyFilledEvent;
import com.dete.common.events.order.OrderRejectedEvent;
import com.dete.common.events.trade.TradeExecutedEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Encapsulates all events produced by processing a single order command in the matching engine. */
public final class MatchResult {

  private OrderAcceptedEvent acceptedEvent;
  private final List<TradeExecutedEvent> trades = new ArrayList<>();
  private final List<OrderFilledEvent> filledEvents = new ArrayList<>();
  private final List<OrderPartiallyFilledEvent> partiallyFilledEvents = new ArrayList<>();
  private OrderCancelledEvent cancelledEvent;
  private OrderRejectedEvent rejectedEvent;

  public static MatchResult rejected(OrderRejectedEvent rejectedEvent) {
    MatchResult result = new MatchResult();
    result.rejectedEvent = rejectedEvent;
    return result;
  }

  public static MatchResult cancelled(OrderCancelledEvent cancelledEvent) {
    MatchResult result = new MatchResult();
    result.cancelledEvent = cancelledEvent;
    return result;
  }

  public void setAcceptedEvent(OrderAcceptedEvent acceptedEvent) {
    this.acceptedEvent = acceptedEvent;
  }

  public void addTrade(TradeExecutedEvent trade) {
    this.trades.add(trade);
  }

  public void addFilledEvent(OrderFilledEvent filled) {
    this.filledEvents.add(filled);
  }

  public void addPartiallyFilledEvent(OrderPartiallyFilledEvent partial) {
    this.partiallyFilledEvents.add(partial);
  }

  public void setCancelledEvent(OrderCancelledEvent cancelledEvent) {
    this.cancelledEvent = cancelledEvent;
  }

  public void setRejectedEvent(OrderRejectedEvent rejectedEvent) {
    this.rejectedEvent = rejectedEvent;
  }

  public OrderAcceptedEvent acceptedEvent() {
    return acceptedEvent;
  }

  public List<TradeExecutedEvent> trades() {
    return Collections.unmodifiableList(trades);
  }

  public List<OrderFilledEvent> filledEvents() {
    return Collections.unmodifiableList(filledEvents);
  }

  public List<OrderPartiallyFilledEvent> partiallyFilledEvents() {
    return Collections.unmodifiableList(partiallyFilledEvents);
  }

  public OrderCancelledEvent cancelledEvent() {
    return cancelledEvent;
  }

  public OrderRejectedEvent rejectedEvent() {
    return rejectedEvent;
  }

  public boolean isRejected() {
    return rejectedEvent != null;
  }

  public boolean hasTrades() {
    return !trades.isEmpty();
  }

  /**
   * Returns all produced events in standard chronological order: accepted -> trades and fill events
   * -> remaining cancellation / rejection.
   */
  public List<DeteEvent> allEvents() {
    List<DeteEvent> all = new ArrayList<>();
    if (acceptedEvent != null) {
      all.add(acceptedEvent);
    }
    all.addAll(trades);
    all.addAll(filledEvents);
    all.addAll(partiallyFilledEvents);
    if (cancelledEvent != null) {
      all.add(cancelledEvent);
    }
    if (rejectedEvent != null) {
      all.add(rejectedEvent);
    }
    return all;
  }
}

package com.dete.matching.engine.model;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.domain.enums.OrderStatus;
import com.dete.common.domain.enums.OrderType;
import com.dete.common.events.order.OrderPlacedEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * High-performance, mutable in-memory representation of an active order in the OrderBook. All price
 * and quantity fields use 8-decimal fixed-point scale.
 */
public final class BookOrder {

  private final UUID orderId;
  private final UUID accountId;
  private final Instrument instrument;
  private final OrderSide side;
  private final OrderType orderType;
  private final long price;
  private final long originalQuantity;
  private long remainingQuantity;
  private long sequenceNumber;
  private OrderStatus status;
  private final Instant timestamp;

  public BookOrder(
      UUID orderId,
      UUID accountId,
      Instrument instrument,
      OrderSide side,
      OrderType orderType,
      long price,
      long originalQuantity,
      long remainingQuantity,
      long sequenceNumber,
      OrderStatus status,
      Instant timestamp) {
    this.orderId = Objects.requireNonNull(orderId, "orderId cannot be null");
    this.accountId = Objects.requireNonNull(accountId, "accountId cannot be null");
    this.instrument = Objects.requireNonNull(instrument, "instrument cannot be null");
    this.side = Objects.requireNonNull(side, "side cannot be null");
    this.orderType = Objects.requireNonNull(orderType, "orderType cannot be null");
    this.price = price;
    this.originalQuantity = originalQuantity;
    this.remainingQuantity = remainingQuantity;
    this.sequenceNumber = sequenceNumber;
    this.status = Objects.requireNonNull(status, "status cannot be null");
    this.timestamp = Objects.requireNonNull(timestamp, "timestamp cannot be null");
  }

  public static BookOrder fromEvent(OrderPlacedEvent event, long sequenceNumber) {
    return new BookOrder(
        event.orderId(),
        event.accountId(),
        event.instrument(),
        event.side(),
        event.orderType(),
        event.price(),
        event.quantity(),
        event.quantity(),
        sequenceNumber,
        OrderStatus.ACCEPTED,
        event.timestamp());
  }

  public UUID orderId() {
    return orderId;
  }

  public UUID accountId() {
    return accountId;
  }

  public Instrument instrument() {
    return instrument;
  }

  public OrderSide side() {
    return side;
  }

  public OrderType orderType() {
    return orderType;
  }

  public long price() {
    return price;
  }

  public long originalQuantity() {
    return originalQuantity;
  }

  public long remainingQuantity() {
    return remainingQuantity;
  }

  public long filledQuantity() {
    return originalQuantity - remainingQuantity;
  }

  public long sequenceNumber() {
    return sequenceNumber;
  }

  public OrderStatus status() {
    return status;
  }

  public Instant timestamp() {
    return timestamp;
  }

  public boolean isFilled() {
    return remainingQuantity <= 0;
  }

  public void fill(long fillQty) {
    if (fillQty <= 0) {
      throw new IllegalArgumentException("Fill quantity must be positive, got: " + fillQty);
    }
    if (fillQty > remainingQuantity) {
      throw new IllegalArgumentException(
          "Fill quantity " + fillQty + " exceeds remaining quantity " + remainingQuantity);
    }
    this.remainingQuantity -= fillQty;
    if (this.remainingQuantity == 0) {
      this.status = OrderStatus.FILLED;
    } else {
      this.status = OrderStatus.PARTIALLY_FILLED;
    }
  }

  public void cancel() {
    this.status = OrderStatus.CANCELLED;
  }

  public void updateSequenceNumber(long sequenceNumber) {
    this.sequenceNumber = sequenceNumber;
  }

  @Override
  public String toString() {
    return "BookOrder{"
        + "orderId="
        + orderId
        + ", accountId="
        + accountId
        + ", instrument="
        + instrument
        + ", side="
        + side
        + ", orderType="
        + orderType
        + ", price="
        + price
        + ", origQty="
        + originalQuantity
        + ", remQty="
        + remainingQuantity
        + ", seq="
        + sequenceNumber
        + ", status="
        + status
        + '}';
  }
}

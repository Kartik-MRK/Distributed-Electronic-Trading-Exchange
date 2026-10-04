package com.dete.matching.engine.model;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Represents an aggregated price level in the order book. Orders at this level are held in an
 * ArrayDeque to enforce strict FIFO time priority.
 */
public final class PriceLevel {

  private final long price;
  private final Deque<BookOrder> orders;
  private long totalVolume;

  public PriceLevel(long price) {
    this.price = price;
    this.orders = new ArrayDeque<>();
    this.totalVolume = 0;
  }

  public long price() {
    return price;
  }

  public Deque<BookOrder> orders() {
    return orders;
  }

  public long totalVolume() {
    return totalVolume;
  }

  public int orderCount() {
    return orders.size();
  }

  public boolean isEmpty() {
    return orders.isEmpty();
  }

  public void addOrder(BookOrder order) {
    orders.addLast(order);
    totalVolume += order.remainingQuantity();
  }

  public boolean removeOrder(BookOrder order) {
    if (orders.remove(order)) {
      totalVolume -= order.remainingQuantity();
      return true;
    }
    return false;
  }

  public BookOrder peek() {
    return orders.peekFirst();
  }

  public BookOrder poll() {
    BookOrder order = orders.pollFirst();
    if (order != null) {
      totalVolume -= order.remainingQuantity();
    }
    return order;
  }

  public void reduceVolume(long fillQty) {
    this.totalVolume -= fillQty;
  }

  @Override
  public String toString() {
    return "PriceLevel{"
        + "price="
        + price
        + ", count="
        + orders.size()
        + ", volume="
        + totalVolume
        + '}';
  }
}

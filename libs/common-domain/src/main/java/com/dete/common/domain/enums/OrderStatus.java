package com.dete.common.domain.enums;

/** Lifecycle status of an order. Transitions are monotonically forward only. */
public enum OrderStatus {
  SUBMITTED,
  ACCEPTED,
  PARTIALLY_FILLED,
  FILLED,
  CANCELLED,
  REJECTED;

  /** Returns true if no further state transitions are possible. */
  public boolean isTerminal() {
    return this == FILLED || this == CANCELLED || this == REJECTED;
  }

  /** Validate that a transition from this status to next is legal. */
  public boolean canTransitionTo(OrderStatus next) {
    return switch (this) {
      case SUBMITTED -> next == ACCEPTED || next == REJECTED || next == CANCELLED;
      case ACCEPTED -> next == PARTIALLY_FILLED || next == FILLED || next == CANCELLED;
      case PARTIALLY_FILLED -> next == PARTIALLY_FILLED || next == FILLED || next == CANCELLED;
      default -> false; // terminal states cannot transition
    };
  }
}

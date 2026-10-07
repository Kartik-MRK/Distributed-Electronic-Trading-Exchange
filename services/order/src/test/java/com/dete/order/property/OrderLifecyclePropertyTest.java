package com.dete.order.property;

import static org.assertj.core.api.Assertions.assertThat;

import com.dete.common.domain.enums.OrderStatus;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class OrderLifecyclePropertyTest {

  @Provide
  Arbitrary<List<OrderStatus>> randomStatusTransitions() {
    return Arbitraries.of(OrderStatus.values()).list().ofMinSize(2).ofMaxSize(30);
  }

  @Property(tries = 200)
  void orderTransitionsAreStrictlyMonotonicAndTerminalStatesAreAbsorbing(
      @ForAll("randomStatusTransitions") List<OrderStatus> attemptedTransitions) {
    OrderStatus current = OrderStatus.SUBMITTED;
    boolean enteredTerminal = false;

    for (OrderStatus attemptedNext : attemptedTransitions) {
      if (current.isTerminal()) {
        enteredTerminal = true;
        // Invariant 1: Terminal states (FILLED, CANCELLED, REJECTED) are absorbing and can never
        // transition
        assertThat(current.canTransitionTo(attemptedNext))
            .as("Terminal state %s must never allow transition to %s", current, attemptedNext)
            .isFalse();
      } else {
        if (current.canTransitionTo(attemptedNext)) {
          // Invariant 2: Monotonic forward progression
          if (current == OrderStatus.ACCEPTED) {
            assertThat(attemptedNext)
                .isIn(OrderStatus.PARTIALLY_FILLED, OrderStatus.FILLED, OrderStatus.CANCELLED);
          } else if (current == OrderStatus.PARTIALLY_FILLED) {
            assertThat(attemptedNext)
                .isIn(OrderStatus.PARTIALLY_FILLED, OrderStatus.FILLED, OrderStatus.CANCELLED);
          } else if (current == OrderStatus.SUBMITTED) {
            assertThat(attemptedNext)
                .isIn(OrderStatus.ACCEPTED, OrderStatus.REJECTED, OrderStatus.CANCELLED);
          }
          current = attemptedNext;
        }
      }

      // Invariant 3: Once entered terminal, current status must remain terminal forever
      if (enteredTerminal) {
        assertThat(current.isTerminal()).isTrue();
      }
    }
  }

  @Property(tries = 100)
  void filledNeverTransitionsToPartiallyFilledOrAccepted(
      @ForAll("randomStatusTransitions") List<OrderStatus> attemptedTransitions) {
    OrderStatus status = OrderStatus.FILLED;
    for (OrderStatus next : attemptedTransitions) {
      assertThat(status.canTransitionTo(next)).isFalse();
    }
  }
}

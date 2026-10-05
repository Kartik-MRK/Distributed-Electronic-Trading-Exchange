package com.dete.marketdata.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.events.marketdata.OrderBookSnapshotEvent.SnapshotLevel;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderBookViewTest {

  private OrderBookView orderBookView;

  @BeforeEach
  void setUp() {
    orderBookView = new OrderBookView(Instrument.BTC_USD);
  }

  @Test
  @DisplayName("Should maintain bids descending and asks ascending with accurate depth limiting")
  void testAddOrdersAndSorting() {
    UUID acc1 = UUID.randomUUID();
    UUID o1 = UUID.randomUUID();
    UUID o2 = UUID.randomUUID();
    UUID o3 = UUID.randomUUID();
    UUID o4 = UUID.randomUUID();

    // Add bids
    orderBookView.addOrder(o1, acc1, OrderSide.BUY, 50_000L, 100L);
    orderBookView.addOrder(o2, acc1, OrderSide.BUY, 51_000L, 200L);

    // Add asks
    orderBookView.addOrder(o3, acc1, OrderSide.SELL, 52_000L, 150L);
    orderBookView.addOrder(o4, acc1, OrderSide.SELL, 51_500L, 50L);

    OrderBookSnapshotResponse snapshot = orderBookView.getL2Snapshot(10);

    // Highest bid should be first (51_000, then 50_000)
    assertThat(snapshot.bids()).hasSize(2);
    assertThat(snapshot.bids().get(0).price()).isEqualTo(51_000L);
    assertThat(snapshot.bids().get(0).volume()).isEqualTo(200L);
    assertThat(snapshot.bids().get(1).price()).isEqualTo(50_000L);
    assertThat(snapshot.bids().get(1).volume()).isEqualTo(100L);

    // Lowest ask should be first (51_500, then 52_000)
    assertThat(snapshot.asks()).hasSize(2);
    assertThat(snapshot.asks().get(0).price()).isEqualTo(51_500L);
    assertThat(snapshot.asks().get(0).volume()).isEqualTo(50L);
    assertThat(snapshot.asks().get(1).price()).isEqualTo(52_000L);
    assertThat(snapshot.asks().get(1).volume()).isEqualTo(150L);

    // Test depth limit
    OrderBookSnapshotResponse depth1 = orderBookView.getL2Snapshot(1);
    assertThat(depth1.bids()).hasSize(1);
    assertThat(depth1.bids().get(0).price()).isEqualTo(51_000L);
    assertThat(depth1.asks()).hasSize(1);
    assertThat(depth1.asks().get(0).price()).isEqualTo(51_500L);
  }

  @Test
  @DisplayName("Should decrement volume on partial fill and remove level on full fill")
  void testFills() {
    UUID acc1 = UUID.randomUUID();
    UUID o1 = UUID.randomUUID();

    orderBookView.addOrder(o1, acc1, OrderSide.BUY, 50_000L, 100L);

    // Partial fill of 40L, remaining 60L
    orderBookView.applyFill(o1, 40L, 60L);
    OrderBookSnapshotResponse snapshot1 = orderBookView.getL2Snapshot(10);
    assertThat(snapshot1.bids()).hasSize(1);
    assertThat(snapshot1.bids().get(0).volume()).isEqualTo(60L);

    // Complete remaining fill of 60L, remaining 0L
    orderBookView.applyFill(o1, 60L, 0L);
    OrderBookSnapshotResponse snapshot2 = orderBookView.getL2Snapshot(10);
    assertThat(snapshot2.bids()).isEmpty();
  }

  @Test
  @DisplayName("Should remove level when order is cancelled")
  void testCancellation() {
    UUID acc1 = UUID.randomUUID();
    UUID o1 = UUID.randomUUID();

    orderBookView.addOrder(o1, acc1, OrderSide.SELL, 55_000L, 80L);
    assertThat(orderBookView.getL2Snapshot(10).asks()).hasSize(1);

    orderBookView.cancelOrder(o1, 80L);
    assertThat(orderBookView.getL2Snapshot(10).asks()).isEmpty();
  }

  @Test
  @DisplayName("Should fully resynchronize with external snapshot from Matching Engine")
  void testResync() {
    UUID acc1 = UUID.randomUUID();
    UUID o1 = UUID.randomUUID();
    orderBookView.addOrder(o1, acc1, OrderSide.BUY, 40_000L, 50L);

    List<SnapshotLevel> authoritativeBids =
        List.of(new SnapshotLevel(60_000L, 500L, 3), new SnapshotLevel(59_000L, 250L, 1));
    List<SnapshotLevel> authoritativeAsks =
        List.of(new SnapshotLevel(61_000L, 300L, 2), new SnapshotLevel(62_000L, 400L, 4));

    orderBookView.resync(999L, authoritativeBids, authoritativeAsks);

    OrderBookSnapshotResponse snapshot = orderBookView.getL2Snapshot(10);
    assertThat(snapshot.sequenceNumber()).isEqualTo(999L);
    assertThat(snapshot.bids()).hasSize(2);
    assertThat(snapshot.bids().get(0).price()).isEqualTo(60_000L);
    assertThat(snapshot.bids().get(0).volume()).isEqualTo(500L);
    assertThat(snapshot.asks()).hasSize(2);
    assertThat(snapshot.asks().get(0).price()).isEqualTo(61_000L);
    assertThat(snapshot.asks().get(0).volume()).isEqualTo(300L);
  }
}

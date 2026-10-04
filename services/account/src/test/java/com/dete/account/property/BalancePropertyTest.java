package com.dete.account.property;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class BalancePropertyTest {

  sealed interface BalanceOp permits DepositOp, ReserveOp, ReleaseOp {}

  record DepositOp(long amount) implements BalanceOp {}

  record ReserveOp(long amount) implements BalanceOp {}

  record ReleaseOp(long amount) implements BalanceOp {}

  @Property
  boolean availableAndReservedAlwaysNonNegativeAndSumToTotal(
      @ForAll("operations") List<BalanceOp> operations) {
    long available = 0L;
    long reserved = 0L;
    long totalDeposited = 0L;

    for (BalanceOp op : operations) {
      if (op instanceof DepositOp d) {
        available += d.amount;
        totalDeposited += d.amount;
      } else if (op instanceof ReserveOp r) {
        if (available >= r.amount) {
          available -= r.amount;
          reserved += r.amount;
        }
      } else if (op instanceof ReleaseOp rel) {
        if (reserved >= rel.amount) {
          reserved -= rel.amount;
          available += rel.amount;
        }
      }

      // Financial invariant assertions after EVERY operation
      assertThat(available).isGreaterThanOrEqualTo(0L);
      assertThat(reserved).isGreaterThanOrEqualTo(0L);
      assertThat(available + reserved).isEqualTo(totalDeposited);
    }

    return true;
  }

  @Provide
  Arbitrary<List<BalanceOp>> operations() {
    Arbitrary<DepositOp> deposits =
        Arbitraries.longs().between(1L, 1_000_000_000L).map(DepositOp::new);
    Arbitrary<ReserveOp> reserves =
        Arbitraries.longs().between(1L, 500_000_000L).map(ReserveOp::new);
    Arbitrary<ReleaseOp> releases =
        Arbitraries.longs().between(1L, 500_000_000L).map(ReleaseOp::new);

    Arbitrary<BalanceOp> allOps = Arbitraries.oneOf(deposits, reserves, releases);
    return allOps.list().ofMinSize(5).ofMaxSize(100);
  }
}

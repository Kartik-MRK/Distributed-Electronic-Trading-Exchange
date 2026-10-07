package com.dete.benchmark;

import com.dete.common.domain.types.FixedPoint;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * JMH Benchmark comparing financial arithmetic: 64-bit Fixed-Point (8 decimal places nano-scale) vs
 * java.math.BigDecimal (arbitrary-precision object heap allocation).
 *
 * <p>Demonstrates the massive throughput advantage and zero allocation overhead of fixed-point math
 * on the hot path.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class FixedPointVsBigDecimalBenchmark {

  private static final long PRICE_RAW = 64_325_50000000L; // 64,325.50 USD
  private static final long QTY_RAW = 2_75000000L; // 2.75 BTC
  private static final long SCALE = FixedPoint.SCALE;

  private FixedPoint fpPrice;
  private FixedPoint fpQty;

  private BigDecimal bdPrice;
  private BigDecimal bdQty;

  @Setup(Level.Trial)
  public void setupTrial() {
    fpPrice = FixedPoint.ofScaled(PRICE_RAW);
    fpQty = FixedPoint.ofScaled(QTY_RAW);

    bdPrice = new BigDecimal("64325.50000000");
    bdQty = new BigDecimal("2.75000000");
  }

  // --- 1. Multiplication (Notional value calculation: price * quantity) ---

  @Benchmark
  public long rawLongMultiply() {
    return (PRICE_RAW * QTY_RAW) / SCALE;
  }

  @Benchmark
  public long fixedPointMultiply() {
    return FixedPoint.multiply(PRICE_RAW, QTY_RAW);
  }

  @Benchmark
  public BigDecimal bigDecimalMultiply() {
    return bdPrice.multiply(bdQty).setScale(8, RoundingMode.HALF_UP);
  }

  // --- 2. Addition (Balance credit: balance + amount) ---

  @Benchmark
  public long rawLongAdd() {
    return PRICE_RAW + QTY_RAW;
  }

  @Benchmark
  public FixedPoint fixedPointAdd() {
    return fpPrice.add(fpQty);
  }

  @Benchmark
  public BigDecimal bigDecimalAdd() {
    return bdPrice.add(bdQty);
  }

  // --- 3. Subtraction (Balance debit: balance - amount) ---

  @Benchmark
  public long rawLongSubtract() {
    return PRICE_RAW - QTY_RAW;
  }

  @Benchmark
  public FixedPoint fixedPointSubtract() {
    return fpPrice.subtract(fpQty);
  }

  @Benchmark
  public BigDecimal bigDecimalSubtract() {
    return bdPrice.subtract(bdQty);
  }

  // --- 4. Comparison (Price check / deviation band) ---

  @Benchmark
  public int rawLongCompare() {
    return Long.compare(PRICE_RAW, QTY_RAW);
  }

  @Benchmark
  public int fixedPointCompare() {
    return fpPrice.compareTo(fpQty);
  }

  @Benchmark
  public int bigDecimalCompare() {
    return bdPrice.compareTo(bdQty);
  }

  public static void main(String[] args) throws Exception {
    Options opt =
        new OptionsBuilder()
            .include(FixedPointVsBigDecimalBenchmark.class.getSimpleName())
            .forks(1)
            .warmupIterations(2)
            .measurementIterations(3)
            .build();
    new Runner(opt).run();
  }
}

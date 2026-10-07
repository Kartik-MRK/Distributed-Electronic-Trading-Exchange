package com.dete.benchmark;

import com.dete.matching.engine.model.PriceLevel;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
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
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * JMH Benchmark comparing data structures for the limit order book price ladder: java.util.TreeMap
 * (Red-Black tree, cache-friendly, single-writer thread) vs
 * java.util.concurrent.ConcurrentSkipListMap (Lock-free skip-list, pointer-heavy).
 *
 * <p>Evaluates insertion, lookup, firstKey (best price), poll (level exhaustion), and L2 depth
 * traversal.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class TreeMapVsSkipListBenchmark {

  private static final int INITIAL_LEVELS = 500;
  private static final long BASE_PRICE = 50_000_00000000L;
  private static final long STEP = 10000000L; // 0.10 USD

  private TreeMap<Long, PriceLevel> seededTreeMap;
  private ConcurrentSkipListMap<Long, PriceLevel> seededSkipList;

  private long insertSeq;

  @Setup(Level.Trial)
  public void setupTrial() {
    insertSeq = 1_000_000L;
  }

  @Setup(Level.Iteration)
  public void setupIteration() {
    seededTreeMap = new TreeMap<>(Comparator.reverseOrder());
    seededSkipList = new ConcurrentSkipListMap<>(Comparator.reverseOrder());

    for (int i = 0; i < INITIAL_LEVELS; i++) {
      long price = BASE_PRICE - (i * STEP);
      seededTreeMap.put(price, new PriceLevel(price));
      seededSkipList.put(price, new PriceLevel(price));
    }
  }

  // --- 1. Best Price Lookup (firstKey) ---

  @Benchmark
  public long treeMapFirstKey() {
    return seededTreeMap.firstKey();
  }

  @Benchmark
  public long skipListFirstKey() {
    return seededSkipList.firstKey();
  }

  // --- 2. Random/Shifting Price Level Lookup (get) ---

  @Benchmark
  public PriceLevel treeMapGet() {
    long price = BASE_PRICE - ((insertSeq++ % INITIAL_LEVELS) * STEP);
    return seededTreeMap.get(price);
  }

  @Benchmark
  public PriceLevel skipListGet() {
    long price = BASE_PRICE - ((insertSeq++ % INITIAL_LEVELS) * STEP);
    return seededSkipList.get(price);
  }

  // --- 3. New Level Insertion (put) ---

  @Benchmark
  public PriceLevel treeMapPut() {
    long price = BASE_PRICE + ((++insertSeq) * STEP);
    PriceLevel level = new PriceLevel(price);
    return seededTreeMap.put(price, level);
  }

  @Benchmark
  public PriceLevel skipListPut() {
    long price = BASE_PRICE + ((++insertSeq) * STEP);
    PriceLevel level = new PriceLevel(price);
    return seededSkipList.put(price, level);
  }

  // --- 4. L2 Top-20 Depth Iteration ---

  @Benchmark
  public void treeMapTop20DepthScan(Blackhole bh) {
    int count = 0;
    for (Map.Entry<Long, PriceLevel> entry : seededTreeMap.entrySet()) {
      bh.consume(entry.getKey());
      bh.consume(entry.getValue());
      if (++count >= 20) break;
    }
  }

  @Benchmark
  public void skipListTop20DepthScan(Blackhole bh) {
    int count = 0;
    for (Map.Entry<Long, PriceLevel> entry : seededSkipList.entrySet()) {
      bh.consume(entry.getKey());
      bh.consume(entry.getValue());
      if (++count >= 20) break;
    }
  }

  public static void main(String[] args) throws Exception {
    Options opt =
        new OptionsBuilder()
            .include(TreeMapVsSkipListBenchmark.class.getSimpleName())
            .forks(1)
            .warmupIterations(2)
            .measurementIterations(3)
            .build();
    new Runner(opt).run();
  }
}

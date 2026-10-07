package com.dete.benchmark;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.domain.enums.OrderType;
import com.dete.common.events.order.OrderCancelCommand;
import com.dete.common.events.order.OrderPlacedEvent;
import com.dete.matching.engine.OrderBook;
import com.dete.matching.engine.model.L2Depth;
import com.dete.matching.engine.model.MatchResult;
import com.dete.matching.service.MatchingEngineService;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bare-metal multi-threaded exchange load simulator running natively on Windows JVM. Simulates 50
 * concurrent client traders submitting 100,000 orders across BTC_USD, ETH_USD, SOL_USD. Evaluates
 * single-writer matching dispatchers, latency percentiles, throughput, and invariants.
 */
public class LocalExchangeLoadSimulator {

  private static final int TOTAL_ORDERS = 100_000;
  private static final int CONCURRENT_CLIENTS = 50;

  // Realistic Base prices (nano-scale: 8 decimal places)
  private static final Map<Instrument, Long> BASE_PRICES =
      Map.of(
          Instrument.BTC_USD, 65_000_00000000L,
          Instrument.ETH_USD, 3_500_00000000L,
          Instrument.SOL_USD, 150_00000000L);

  public static void main(String[] args) throws Exception {
    System.out.println(
        "================================================================================");
    System.out.println("  DETE NATIVE WINDOWS BARE-METAL LOAD & PERFORMANCE SIMULATOR");
    System.out.println(
        "================================================================================");
    System.out.println(
        "OS: " + System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")");
    System.out.println(
        "JVM: " + System.getProperty("java.vm.name") + " " + System.getProperty("java.version"));
    System.out.println("Available CPU Processors: " + Runtime.getRuntime().availableProcessors());
    System.out.println(
        "Max JVM Heap: " + (Runtime.getRuntime().maxMemory() / (1024 * 1024)) + " MB");
    System.out.println(
        "Simulating: "
            + TOTAL_ORDERS
            + " orders with "
            + CONCURRENT_CLIENTS
            + " concurrent client threads");
    System.out.println("Instruments: BTC_USD, ETH_USD, SOL_USD");
    System.out.println(
        "--------------------------------------------------------------------------------\n");

    MatchingEngineService matchingService = new MatchingEngineService();

    // Warm-up phase
    System.out.println(
        ">>> [1/4] Warming up JIT compiler and memory structures (10,000 orders)...");
    runWarmup(matchingService);
    matchingService.resetAll();
    System.gc();
    Thread.sleep(1000);

    System.out.println(
        ">>> [2/4] Executing Sustained Bare-Metal Load Test (" + TOTAL_ORDERS + " orders)...");

    // Track GC metrics
    long gcCountBefore = getGcCount();
    long gcTimeBefore = getGcTime();
    long heapBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

    // Latency trackers in nanoseconds
    long[] globalLatencies = new long[TOTAL_ORDERS];
    ConcurrentLinkedQueue<Long> matchLatencies = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<Long> restingLatencies = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<Long> cancelLatencies = new ConcurrentLinkedQueue<>();

    AtomicInteger orderSequence = new AtomicInteger(0);
    AtomicLong totalTradesExecuted = new AtomicLong(0);
    AtomicLong totalVolumeMatched = new AtomicLong(0);
    AtomicLong totalOrdersCancelled = new AtomicLong(0);

    // Pre-create trader accounts and active resting orders pool for cancels
    UUID[] accounts = new UUID[100];
    for (int i = 0; i < accounts.length; i++) {
      accounts[i] = UUID.randomUUID();
    }
    ConcurrentHashMap<Instrument, ConcurrentLinkedQueue<PlacedRecord>> activeRestingOrders =
        new ConcurrentHashMap<>();
    for (Instrument inst : Instrument.values()) {
      activeRestingOrders.put(inst, new ConcurrentLinkedQueue<>());
    }

    ExecutorService clientPool = Executors.newFixedThreadPool(CONCURRENT_CLIENTS);
    CountDownLatch startGate = new CountDownLatch(1);
    CountDownLatch doneGate = new CountDownLatch(CONCURRENT_CLIENTS);

    int ordersPerClient = TOTAL_ORDERS / CONCURRENT_CLIENTS;
    long testStartTimeNanos = System.nanoTime();

    for (int clientId = 0; clientId < CONCURRENT_CLIENTS; clientId++) {
      final int cId = clientId;
      clientPool.submit(
          () -> {
            try {
              startGate.await();
              Random random = new Random(42L + cId);
              Instrument[] instruments =
                  new Instrument[] {Instrument.BTC_USD, Instrument.ETH_USD, Instrument.SOL_USD};

              for (int i = 0; i < ordersPerClient; i++) {
                int seq = orderSequence.getAndIncrement();
                Instrument instrument = instruments[random.nextInt(instruments.length)];
                long basePrice = BASE_PRICES.get(instrument);
                UUID account = accounts[random.nextInt(accounts.length)];

                int actionRoll = random.nextInt(100);
                long opStart = System.nanoTime();

                if (actionRoll < 10) {
                  // 10% CANCEL COMMAND
                  PlacedRecord toCancel = activeRestingOrders.get(instrument).poll();
                  if (toCancel != null) {
                    OrderCancelCommand cancel =
                        OrderCancelCommand.of(toCancel.orderId(), toCancel.accountId(), instrument);
                    MatchResult result = matchingService.cancelOrder(cancel).join();
                    long latencyNanos = System.nanoTime() - opStart;
                    globalLatencies[seq] = latencyNanos;
                    cancelLatencies.add(latencyNanos);
                    if (result.cancelledEvent() != null) {
                      totalOrdersCancelled.incrementAndGet();
                    }
                  } else {
                    // Fallback to resting order if no orders in cancel pool
                    long latencyNanos =
                        submitRestingOrder(
                            matchingService,
                            instrument,
                            basePrice,
                            account,
                            random,
                            activeRestingOrders);
                    globalLatencies[seq] = latencyNanos;
                    restingLatencies.add(latencyNanos);
                  }
                } else if (actionRoll < 40) {
                  // 30% AGGRESSIVE CROSSING LIMIT/MARKET ORDER (Trigger matches)
                  OrderSide side = random.nextBoolean() ? OrderSide.BUY : OrderSide.SELL;
                  // Crossing price: Bids above mid, Asks below mid
                  long tickOffset = (random.nextInt(5) + 1) * 10_00000000L;
                  long price =
                      (side == OrderSide.BUY) ? basePrice + tickOffset : basePrice - tickOffset;
                  long qty = (random.nextInt(5) + 1) * 10000000L; // 0.1 to 0.5 units

                  UUID orderId = UUID.randomUUID();
                  OrderPlacedEvent event =
                      new OrderPlacedEvent(
                          UUID.randomUUID(),
                          orderId,
                          account,
                          instrument,
                          side,
                          OrderType.LIMIT,
                          price,
                          qty,
                          orderId,
                          Instant.now(),
                          1);

                  MatchResult result = matchingService.processOrder(event).join();
                  long latencyNanos = System.nanoTime() - opStart;
                  globalLatencies[seq] = latencyNanos;
                  matchLatencies.add(latencyNanos);

                  if (result.hasTrades()) {
                    totalTradesExecuted.addAndGet(result.trades().size());
                    for (var trade : result.trades()) {
                      totalVolumeMatched.addAndGet(trade.quantity());
                    }
                  }
                } else {
                  // 60% RESTING LIMIT ORDER
                  long latencyNanos =
                      submitRestingOrder(
                          matchingService,
                          instrument,
                          basePrice,
                          account,
                          random,
                          activeRestingOrders);
                  globalLatencies[seq] = latencyNanos;
                  restingLatencies.add(latencyNanos);
                }
              }
            } catch (Exception e) {
              e.printStackTrace();
            } finally {
              doneGate.countDown();
            }
          });
    }

    startGate.countDown();
    doneGate.await(60, TimeUnit.SECONDS);
    long testEndTimeNanos = System.nanoTime();
    clientPool.shutdown();

    long totalDurationNanos = testEndTimeNanos - testStartTimeNanos;
    double totalDurationSeconds = totalDurationNanos / 1_000_000_000.0;
    double overallThroughput = TOTAL_ORDERS / totalDurationSeconds;

    long gcCountAfter = getGcCount();
    long gcTimeAfter = getGcTime();
    long heapAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

    System.out.println(">>> [3/4] Validating Financial Invariants & Order Book Consistency...");
    boolean invariantsPassed = validateInvariants(matchingService);

    System.out.println(">>> [4/4] Computing Latency Percentiles & Compiling Final Report...\n");

    Arrays.sort(globalLatencies);
    long[] matchArr = toSortedArray(matchLatencies);
    long[] restingArr = toSortedArray(restingLatencies);
    long[] cancelArr = toSortedArray(cancelLatencies);

    printResults(
        totalDurationSeconds,
        overallThroughput,
        totalTradesExecuted.get(),
        totalOrdersCancelled.get(),
        totalVolumeMatched.get(),
        globalLatencies,
        matchArr,
        restingArr,
        cancelArr,
        invariantsPassed,
        heapBefore,
        heapAfter,
        (gcCountAfter - gcCountBefore),
        (gcTimeAfter - gcTimeBefore));

    matchingService.shutdown();
  }

  private static long submitRestingOrder(
      MatchingEngineService matchingService,
      Instrument instrument,
      long basePrice,
      UUID account,
      Random random,
      Map<Instrument, ConcurrentLinkedQueue<PlacedRecord>> activeRestingOrders) {

    long opStart = System.nanoTime();
    OrderSide side = random.nextBoolean() ? OrderSide.BUY : OrderSide.SELL;
    // Passive: Bids strictly below base, Asks strictly above base
    long tickOffset = (random.nextInt(200) + 10) * 1_00000000L;
    long price = (side == OrderSide.BUY) ? (basePrice - tickOffset) : (basePrice + tickOffset);
    long qty = (random.nextInt(10) + 1) * 10000000L;

    UUID orderId = UUID.randomUUID();
    OrderPlacedEvent event =
        new OrderPlacedEvent(
            UUID.randomUUID(),
            orderId,
            account,
            instrument,
            side,
            OrderType.LIMIT,
            price,
            qty,
            orderId,
            Instant.now(),
            1);

    MatchResult result = matchingService.processOrder(event).join();
    long latencyNanos = System.nanoTime() - opStart;

    if (result.acceptedEvent() != null && !result.hasTrades()) {
      activeRestingOrders.get(instrument).add(new PlacedRecord(orderId, account));
    }
    return latencyNanos;
  }

  private static void runWarmup(MatchingEngineService matchingService) {
    Random r = new Random(12345);
    UUID warmAcc = UUID.randomUUID();
    for (int i = 0; i < 10_000; i++) {
      Instrument inst = Instrument.BTC_USD;
      OrderSide side = (i % 2 == 0) ? OrderSide.BUY : OrderSide.SELL;
      long price = 65_000_00000000L + (i % 50) * 100000000L;
      UUID id = UUID.randomUUID();
      OrderPlacedEvent event =
          new OrderPlacedEvent(
              UUID.randomUUID(),
              id,
              warmAcc,
              inst,
              side,
              OrderType.LIMIT,
              price,
              1_00000000L,
              id,
              Instant.now(),
              1);
      matchingService.processOrder(event).join();
    }
  }

  private static boolean validateInvariants(MatchingEngineService matchingService) {
    boolean allValid = true;
    for (Instrument inst : Instrument.values()) {
      L2Depth depth = matchingService.getL2Depth(inst, 50).join();
      OrderBook book = matchingService.getOrderBook(inst);

      System.out.printf(
          "  [%s] L2 Depth: %d bid levels, %d ask levels | Sequence: %d%n",
          inst.symbol(), depth.bids().size(), depth.asks().size(), book.currentSequenceNumber());

      if (!depth.bids().isEmpty() && !depth.asks().isEmpty()) {
        long bestBid = depth.bids().get(0).price();
        long bestAsk = depth.asks().get(0).price();
        if (bestBid >= bestAsk) {
          System.err.printf(
              "  [INVARIANT VIOLATION] Book is crossed! Best Bid (%d) >= Best Ask (%d)%n",
              bestBid, bestAsk);
          allValid = false;
        } else {
          System.out.printf(
              "  [INVARIANT VERIFIED] Spread intact: Best Bid = %,d | Best Ask = %,d | Spread = %,d%n",
              bestBid, bestAsk, (bestAsk - bestBid));
        }
      }
    }
    return allValid;
  }

  private static void printResults(
      double durationSeconds,
      double throughput,
      long totalTrades,
      long totalCancels,
      long totalVolume,
      long[] global,
      long[] matches,
      long[] resting,
      long[] cancels,
      boolean invariantsPassed,
      long heapBefore,
      long heapAfter,
      long gcCount,
      long gcTimeMs) {

    System.out.println(
        "================================================================================");
    System.out.println(
        "                    NATIVE WINDOWS LOAD SIMULATION RESULTS                      ");
    System.out.println(
        "================================================================================");
    System.out.printf("Total Workload:            %,d operations%n", TOTAL_ORDERS);
    System.out.printf("Concurrent Client Threads: %d threads%n", CONCURRENT_CLIENTS);
    System.out.printf("Total Elapsed Time:        %.4f seconds%n", durationSeconds);
    System.out.printf("Sustained Throughput:      %,.2f ops/sec%n", throughput);
    System.out.printf("Total Trades Executed:     %,d trades%n", totalTrades);
    System.out.printf("Total Cancels Processed:   %,d cancels%n", totalCancels);
    System.out.printf("Total Base Units Matched:  %,.4f units%n", (totalVolume / 100_000_000.0));
    System.out.printf(
        "Invariants Verified:       %s%n",
        invariantsPassed ? "PASSED (Zero Invariant Violations)" : "FAILED");
    System.out.println(
        "--------------------------------------------------------------------------------");
    System.out.println("LATENCY PERCENTILES (Microseconds):");
    System.out.println(
        "Category         | Min (us) | P50 (us) | P90 (us) | P95 (us) | P99 (us) | P99.9(us)| Max (us) ");
    System.out.println(
        "-----------------+----------+----------+----------+----------+----------+----------+----------");
    printLatencyRow("Overall (Global)", global);
    printLatencyRow("Crossing Match  ", matches);
    printLatencyRow("Resting Insert  ", resting);
    printLatencyRow("O(1) Cancel     ", cancels);
    System.out.println(
        "--------------------------------------------------------------------------------");
    System.out.println("JVM & GC PROFILE UNDER LOAD:");
    System.out.printf("Heap Used (Start):         %,d MB%n", heapBefore / (1024 * 1024));
    System.out.printf("Heap Used (End):           %,d MB%n", heapAfter / (1024 * 1024));
    System.out.printf("Young/Old GC Invocations:  %d collections%n", gcCount);
    System.out.printf("Total GC Pause Time:       %d ms%n", gcTimeMs);
    System.out.println(
        "================================================================================\n");
  }

  private static void printLatencyRow(String label, long[] latenciesNanos) {
    if (latenciesNanos == null || latenciesNanos.length == 0) {
      System.out.printf(
          "%-17s|      N/A |      N/A |      N/A |      N/A |      N/A |      N/A |      N/A%n",
          label);
      return;
    }
    double min = latenciesNanos[0] / 1000.0;
    double p50 = percentile(latenciesNanos, 0.50) / 1000.0;
    double p90 = percentile(latenciesNanos, 0.90) / 1000.0;
    double p95 = percentile(latenciesNanos, 0.95) / 1000.0;
    double p99 = percentile(latenciesNanos, 0.99) / 1000.0;
    double p999 = percentile(latenciesNanos, 0.999) / 1000.0;
    double max = latenciesNanos[latenciesNanos.length - 1] / 1000.0;

    System.out.printf(
        "%-17s| %8.2f | %8.2f | %8.2f | %8.2f | %8.2f | %8.2f | %8.2f%n",
        label, min, p50, p90, p95, p99, p999, max);
  }

  private static long percentile(long[] sorted, double pct) {
    int index = (int) Math.ceil(pct * sorted.length) - 1;
    return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
  }

  private static long[] toSortedArray(ConcurrentLinkedQueue<Long> queue) {
    long[] arr = new long[queue.size()];
    int i = 0;
    for (Long v : queue) {
      if (i < arr.length) {
        arr[i++] = v;
      }
    }
    Arrays.sort(arr);
    return arr;
  }

  private static long getGcCount() {
    long count = 0;
    for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
      long c = gc.getCollectionCount();
      if (c > 0) count += c;
    }
    return count;
  }

  private static long getGcTime() {
    long time = 0;
    for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
      long t = gc.getCollectionTime();
      if (t > 0) time += t;
    }
    return time;
  }

  private record PlacedRecord(UUID orderId, UUID accountId) {}
}

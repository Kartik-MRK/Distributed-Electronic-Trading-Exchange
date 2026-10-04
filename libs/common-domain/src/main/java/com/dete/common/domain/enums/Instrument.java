package com.dete.common.domain.enums;

/** Trading instruments supported by the exchange. Instrument also encodes Kafka partition. */
public enum Instrument {
  BTC_USD("BTC-USD", "BTC", "USD", 0),
  ETH_USD("ETH-USD", "ETH", "USD", 1),
  SOL_USD("SOL-USD", "SOL", "USD", 2);

  private final String symbol;
  private final String baseAsset;
  private final String quoteAsset;
  private final int kafkaPartition;

  Instrument(String symbol, String baseAsset, String quoteAsset, int kafkaPartition) {
    this.symbol = symbol;
    this.baseAsset = baseAsset;
    this.quoteAsset = quoteAsset;
    this.kafkaPartition = kafkaPartition;
  }

  public String symbol() {
    return symbol;
  }

  public String baseAsset() {
    return baseAsset;
  }

  public String quoteAsset() {
    return quoteAsset;
  }

  public int kafkaPartition() {
    return kafkaPartition;
  }

  public static Instrument fromSymbol(String symbol) {
    for (Instrument i : values()) {
      if (i.symbol.equals(symbol)) return i;
    }
    throw new IllegalArgumentException("Unknown instrument symbol: " + symbol);
  }
}

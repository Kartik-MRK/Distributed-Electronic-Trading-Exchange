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

  @com.fasterxml.jackson.annotation.JsonCreator
  public static Instrument fromString(String val) {
    if (val == null || val.isBlank()) return null;
    String normalized = val.trim().toUpperCase().replace("-", "_");
    if (normalized.equals("BTC_USDT") || normalized.equals("BTC_USD")) return BTC_USD;
    if (normalized.equals("ETH_USDT") || normalized.equals("ETH_USD")) return ETH_USD;
    if (normalized.equals("SOL_USDT") || normalized.equals("SOL_USD")) return SOL_USD;
    for (Instrument i : values()) {
      if (i.name().equalsIgnoreCase(normalized) || i.symbol.equalsIgnoreCase(val.trim())) {
        return i;
      }
    }
    throw new IllegalArgumentException("Unknown instrument: " + val);
  }

  public static Instrument fromSymbol(String symbol) {
    for (Instrument i : values()) {
      if (i.symbol.equals(symbol)) return i;
    }
    throw new IllegalArgumentException("Unknown instrument symbol: " + symbol);
  }
}

package com.dete.marketdata.model;

/**
 * OHLCV Candle representation for Market Data Service. All financial quantities (open, high, low,
 * close, volume) are fixed-point scaled longs.
 */
public record Candle(
    long openTime, // window start epoch millis
    long closeTime, // window end epoch millis
    long open, // open price
    long high, // high price
    long low, // low price
    long close, // close price
    long volume, // total volume traded in window
    int tradeCount // total count of trades in window
    ) {

  public static Candle ofFirstTrade(long windowStart, long windowEnd, long price, long quantity) {
    return new Candle(windowStart, windowEnd, price, price, price, price, quantity, 1);
  }

  public Candle withTrade(long price, long quantity) {
    return new Candle(
        this.openTime,
        this.closeTime,
        this.open,
        Math.max(this.high, price),
        Math.min(this.low, price),
        price,
        this.volume + quantity,
        this.tradeCount + 1);
  }
}

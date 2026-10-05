package com.dete.risk.config;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.types.FixedPoint;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "risk.rules")
public class RiskProperties {

  private long maxBtcQty = 100L * FixedPoint.SCALE;
  private long maxEthQty = 1_000L * FixedPoint.SCALE;
  private long maxSolQty = 10_000L * FixedPoint.SCALE;
  private long maxOrderNotional = 5_000_000L * FixedPoint.SCALE;
  private int maxOpenOrdersPerAccount = 50;
  private double maxPriceDeviationPct = 0.10;
  private double marketOrderMaxQtyRatio = 0.50;

  public long getMaxQty(Instrument instrument) {
    return switch (instrument) {
      case BTC_USD -> maxBtcQty;
      case ETH_USD -> maxEthQty;
      case SOL_USD -> maxSolQty;
    };
  }

  public long getMaxBtcQty() {
    return maxBtcQty;
  }

  public void setMaxBtcQty(long maxBtcQty) {
    this.maxBtcQty = maxBtcQty;
  }

  public long getMaxEthQty() {
    return maxEthQty;
  }

  public void setMaxEthQty(long maxEthQty) {
    this.maxEthQty = maxEthQty;
  }

  public long getMaxSolQty() {
    return maxSolQty;
  }

  public void setMaxSolQty(long maxSolQty) {
    this.maxSolQty = maxSolQty;
  }

  public long getMaxOrderNotional() {
    return maxOrderNotional;
  }

  public void setMaxOrderNotional(long maxOrderNotional) {
    this.maxOrderNotional = maxOrderNotional;
  }

  public int getMaxOpenOrdersPerAccount() {
    return maxOpenOrdersPerAccount;
  }

  public void setMaxOpenOrdersPerAccount(int maxOpenOrdersPerAccount) {
    this.maxOpenOrdersPerAccount = maxOpenOrdersPerAccount;
  }

  public double getMaxPriceDeviationPct() {
    return maxPriceDeviationPct;
  }

  public void setMaxPriceDeviationPct(double maxPriceDeviationPct) {
    this.maxPriceDeviationPct = maxPriceDeviationPct;
  }

  public double getMarketOrderMaxQtyRatio() {
    return marketOrderMaxQtyRatio;
  }

  public void setMarketOrderMaxQtyRatio(double marketOrderMaxQtyRatio) {
    this.marketOrderMaxQtyRatio = marketOrderMaxQtyRatio;
  }
}

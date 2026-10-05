package com.dete.simulator.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "simulator")
public class SimulatorProperties {

  private boolean enabled = true;
  private boolean seedDemo = true;
  private String authUrl = "http://localhost:8081";
  private String accountUrl = "http://localhost:8082";
  private String orderUrl = "http://localhost:8083";
  private int driftIntervalSeconds = 10;
  private int tradeIntervalSeconds = 3;
  private int refreshIntervalSeconds = 4;
  private List<InstrumentConfig> instruments = new ArrayList<>();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isSeedDemo() {
    return seedDemo;
  }

  public void setSeedDemo(boolean seedDemo) {
    this.seedDemo = seedDemo;
  }

  public String getAuthUrl() {
    return authUrl;
  }

  public void setAuthUrl(String authUrl) {
    this.authUrl = authUrl;
  }

  public String getAccountUrl() {
    return accountUrl;
  }

  public void setAccountUrl(String accountUrl) {
    this.accountUrl = accountUrl;
  }

  public String getOrderUrl() {
    return orderUrl;
  }

  public void setOrderUrl(String orderUrl) {
    this.orderUrl = orderUrl;
  }

  public int getDriftIntervalSeconds() {
    return driftIntervalSeconds;
  }

  public void setDriftIntervalSeconds(int driftIntervalSeconds) {
    this.driftIntervalSeconds = driftIntervalSeconds;
  }

  public int getTradeIntervalSeconds() {
    return tradeIntervalSeconds;
  }

  public void setTradeIntervalSeconds(int tradeIntervalSeconds) {
    this.tradeIntervalSeconds = tradeIntervalSeconds;
  }

  public int getRefreshIntervalSeconds() {
    return refreshIntervalSeconds;
  }

  public void setRefreshIntervalSeconds(int refreshIntervalSeconds) {
    this.refreshIntervalSeconds = refreshIntervalSeconds;
  }

  public List<InstrumentConfig> getInstruments() {
    return instruments;
  }

  public void setInstruments(List<InstrumentConfig> instruments) {
    this.instruments = instruments;
  }

  public static class InstrumentConfig {
    private String symbol;
    private double initialMid;
    private int spreadBps = 20;
    private int levels = 15;
    private double orderSizeMin = 0.001;
    private double orderSizeMax = 0.1;

    public InstrumentConfig() {}

    public InstrumentConfig(
        String symbol,
        double initialMid,
        int spreadBps,
        int levels,
        double orderSizeMin,
        double orderSizeMax) {
      this.symbol = symbol;
      this.initialMid = initialMid;
      this.spreadBps = spreadBps;
      this.levels = levels;
      this.orderSizeMin = orderSizeMin;
      this.orderSizeMax = orderSizeMax;
    }

    public String getSymbol() {
      return symbol;
    }

    public void setSymbol(String symbol) {
      this.symbol = symbol;
    }

    public double getInitialMid() {
      return initialMid;
    }

    public void setInitialMid(double initialMid) {
      this.initialMid = initialMid;
    }

    public int getSpreadBps() {
      return spreadBps;
    }

    public void setSpreadBps(int spreadBps) {
      this.spreadBps = spreadBps;
    }

    public int getLevels() {
      return levels;
    }

    public void setLevels(int levels) {
      this.levels = levels;
    }

    public double getOrderSizeMin() {
      return orderSizeMin;
    }

    public void setOrderSizeMin(double orderSizeMin) {
      this.orderSizeMin = orderSizeMin;
    }

    public double getOrderSizeMax() {
      return orderSizeMax;
    }

    public void setOrderSizeMax(double orderSizeMax) {
      this.orderSizeMax = orderSizeMax;
    }
  }
}

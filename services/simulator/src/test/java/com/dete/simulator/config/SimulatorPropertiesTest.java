package com.dete.simulator.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SimulatorPropertiesTest {

  @Test
  @DisplayName("Should initialize with valid default settings")
  void shouldHaveCorrectDefaults() {
    SimulatorProperties props = new SimulatorProperties();

    assertThat(props.isEnabled()).isTrue();
    assertThat(props.isSeedDemo()).isTrue();
    assertThat(props.getAuthUrl()).isEqualTo("http://localhost:8081");
    assertThat(props.getAccountUrl()).isEqualTo("http://localhost:8082");
    assertThat(props.getOrderUrl()).isEqualTo("http://localhost:8083");
    assertThat(props.getDriftIntervalSeconds()).isEqualTo(10);
    assertThat(props.getTradeIntervalSeconds()).isEqualTo(3);
    assertThat(props.getRefreshIntervalSeconds()).isEqualTo(4);
    assertThat(props.getInstruments()).isEmpty();
  }

  @Test
  @DisplayName("Should store configured instruments properly")
  void shouldStoreConfiguredInstruments() {
    SimulatorProperties props = new SimulatorProperties();
    SimulatorProperties.InstrumentConfig btc =
        new SimulatorProperties.InstrumentConfig("BTC-USD", 65000.0, 20, 15, 0.001, 0.1);
    props.setInstruments(List.of(btc));

    assertThat(props.getInstruments()).hasSize(1);
    assertThat(props.getInstruments().get(0).getSymbol()).isEqualTo("BTC-USD");
    assertThat(props.getInstruments().get(0).getInitialMid()).isEqualTo(65000.0);
    assertThat(props.getInstruments().get(0).getSpreadBps()).isEqualTo(20);
    assertThat(props.getInstruments().get(0).getLevels()).isEqualTo(15);
  }
}

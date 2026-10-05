package com.dete.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.dete.simulator.client.ExchangeRestClient;
import com.dete.simulator.config.SimulatorProperties;
import com.dete.simulator.service.BotAccountManager;
import com.dete.simulator.service.SimulatorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(classes = SimulatorApplication.class)
class SimulatorIntegrationTest {

  @Autowired private SimulatorProperties properties;
  @Autowired private SimulatorService simulatorService;
  @Autowired private BotAccountManager accountManager;

  @MockBean private ExchangeRestClient restClient;

  @Test
  @DisplayName("Should successfully load SimulatorApplication context and wire components")
  void contextLoads() {
    assertThat(properties).isNotNull();
    assertThat(simulatorService).isNotNull();
    assertThat(accountManager).isNotNull();
    assertThat(properties.getInstruments()).hasSize(3);
  }
}

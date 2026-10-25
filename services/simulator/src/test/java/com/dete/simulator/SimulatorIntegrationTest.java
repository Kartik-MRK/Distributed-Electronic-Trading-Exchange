package com.dete.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dete.simulator.client.ExchangeRestClient;
import com.dete.simulator.config.SimulatorProperties;
import com.dete.simulator.service.SimulatorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = SimulatorApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:simulator;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.flyway.enabled=false",
      "security.jwt.allow-development-key=true"
    })
@AutoConfigureMockMvc
class SimulatorIntegrationTest {
  @Autowired private SimulatorProperties properties;
  @Autowired private SimulatorService simulatorService;
  @MockBean private ExchangeRestClient restClient;
  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("Simulator context loads without starting market activity while idle")
  void contextLoadsIdle() {
    assertThat(properties.getInstruments()).hasSize(3);
    assertThat(properties.getInstruments())
        .extracting(SimulatorProperties.InstrumentConfig::getSymbol)
        .containsExactly("BTC-USD", "ETH-USD", "SOL-USD");
    assertThat(properties.getInstruments())
        .extracting(SimulatorProperties.InstrumentConfig::getLevels)
        .containsOnly(6);
    assertThat(simulatorService).isNotNull();
    verifyNoInteractions(restClient);
  }

  @Test
  @DisplayName("Direct access to simulation status requires a valid JWT")
  void statusRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/market-simulation/status")).andExpect(status().isUnauthorized());
  }
}

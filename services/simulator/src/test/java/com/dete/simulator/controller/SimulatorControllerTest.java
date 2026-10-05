package com.dete.simulator.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dete.simulator.config.SimulatorProperties;
import com.dete.simulator.service.BotAccountManager;
import com.dete.simulator.service.SimulatorService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SimulatorController.class)
class SimulatorControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private SimulatorService simulatorService;
  @MockBean private SimulatorProperties properties;
  @MockBean private BotAccountManager accountManager;

  @Test
  @DisplayName("GET /simulator/status returns runtime status and active bots")
  void shouldReturnSimulatorStatus() throws Exception {
    when(properties.isEnabled()).thenReturn(true);
    when(simulatorService.isRunning()).thenReturn(true);
    when(simulatorService.isInitialized()).thenReturn(true);
    when(simulatorService.getBotStatuses())
        .thenReturn(
            List.of(
                new SimulatorService.BotStatusDto("BTC-USD", 65000.0, 15, 15)));

    mockMvc
        .perform(get("/simulator/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(true))
        .andExpect(jsonPath("$.running").value(true))
        .andExpect(jsonPath("$.initialized").value(true))
        .andExpect(jsonPath("$.bots[0].instrument").value("BTC-USD"))
        .andExpect(jsonPath("$.bots[0].currentMid").value(65000.0))
        .andExpect(jsonPath("$.bots[0].activeBids").value(15))
        .andExpect(jsonPath("$.bots[0].activeAsks").value(15));
  }

  @Test
  @DisplayName("POST /simulator/start resumes simulator execution")
  void shouldStartSimulator() throws Exception {
    mockMvc
        .perform(post("/simulator/start"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.running").value(true));

    verify(simulatorService).start();
  }

  @Test
  @DisplayName("POST /simulator/stop pauses simulator execution")
  void shouldStopSimulator() throws Exception {
    mockMvc
        .perform(post("/simulator/stop"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.running").value(false));

    verify(simulatorService).stop();
  }

  @Test
  @DisplayName("POST /simulator/seed triggers account and demo funding")
  void shouldSeedAccounts() throws Exception {
    mockMvc
        .perform(post("/simulator/seed"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Demo and bot accounts seeded successfully"));

    verify(accountManager).initialize();
  }
}

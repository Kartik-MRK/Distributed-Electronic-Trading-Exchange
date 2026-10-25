package com.dete.simulator.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dete.simulator.service.SimulationSession;
import com.dete.simulator.service.SimulatorService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SimulatorControllerTest {
  private final SimulatorService simulatorService = mock(SimulatorService.class);
  private final SimulatorController controller = new SimulatorController(simulatorService);

  @Test
  void statusReturnsIdleWhenNoSessionHasRun() throws Exception {
    when(simulatorService.getStatus()).thenReturn(null);
    assertThat(controller.getStatus().getBody().status()).isEqualTo("IDLE");
  }

  @Test
  void authenticatedUserCanStartSupportedSession() throws Exception {
    UUID userId = UUID.randomUUID();
    SimulationSession session =
        new SimulationSession(
            UUID.randomUUID(),
            userId,
            "ACTIVE",
            30,
            Instant.now(),
            Instant.now().plusSeconds(30),
            null,
            null);
    when(simulatorService.start(userId, 30))
        .thenReturn(new SimulatorService.StartResult(true, session));
    var response = controller.start(userId, new SimulatorController.StartRequest(30));
    assertThat(response.getStatusCode().value()).isEqualTo(202);
    assertThat(response.getBody().status()).isEqualTo("ACTIVE");
    assertThat(response.getBody().durationSeconds()).isEqualTo(30);
  }

  @Test
  void concurrentSessionIsReturnedAsConflict() throws Exception {
    UUID userId = UUID.randomUUID();
    SimulationSession session =
        new SimulationSession(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "ACTIVE",
            120,
            Instant.now(),
            Instant.now().plusSeconds(120),
            null,
            null);
    when(simulatorService.start(userId, 120))
        .thenReturn(new SimulatorService.StartResult(false, session));
    when(simulatorService.start(userId, 120))
        .thenReturn(new SimulatorService.StartResult(false, session));
    var response = controller.start(userId, new SimulatorController.StartRequest(120));
    assertThat(response.getStatusCode().value()).isEqualTo(409);
    assertThat(response.getBody().sessionId()).isEqualTo(session.sessionId());
  }

  @Test
  void unsupportedDurationIsRejected() throws Exception {
    UUID userId = UUID.randomUUID();
    when(simulatorService.start(userId, 45))
        .thenThrow(new IllegalArgumentException("Duration must be 30, 60, or 120 seconds"));
    var response = controller.start(userId, new SimulatorController.StartRequest(45));
    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getBody().message()).isEqualTo("Duration must be 30, 60, or 120 seconds");
  }
}

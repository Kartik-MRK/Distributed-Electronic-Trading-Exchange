package com.dete.simulator.controller;

import com.dete.simulator.service.SimulationSession;
import com.dete.simulator.service.SimulatorService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/market-simulation")
public class SimulatorController {
  private final SimulatorService simulatorService;

  public SimulatorController(SimulatorService simulatorService) {
    this.simulatorService = simulatorService;
  }

  @GetMapping("/status")
  public ResponseEntity<SessionResponse> getStatus() {
    return ResponseEntity.ok(SessionResponse.from(simulatorService.getStatus()));
  }

  @PostMapping("/start")
  public ResponseEntity<SessionResponse> start(
      @AuthenticationPrincipal UUID userId, @RequestBody StartRequest request) {
    try {
      SimulatorService.StartResult result =
          simulatorService.start(userId, request.durationSeconds());
      SessionResponse response = SessionResponse.from(result.session());
      return ResponseEntity.status(result.started() ? HttpStatus.ACCEPTED : HttpStatus.CONFLICT)
          .body(response);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(SessionResponse.error(e.getMessage()));
    } catch (IllegalStateException e) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(SessionResponse.error(e.getMessage()));
    }
  }

  public record StartRequest(int durationSeconds) {}

  public record SessionResponse(
      String status,
      UUID sessionId,
      UUID ownerUserId,
      Integer durationSeconds,
      Instant startsAt,
      Instant expiresAt,
      Instant completedAt,
      String completionReason,
      String message) {
    static SessionResponse from(SimulationSession session) {
      return session == null
          ? new SessionResponse("IDLE", null, null, null, null, null, null, null, null)
          : new SessionResponse(
              session.status(),
              session.sessionId(),
              session.ownerUserId(),
              session.durationSeconds(),
              session.startsAt(),
              session.expiresAt(),
              session.completedAt(),
              session.completionReason(),
              null);
    }

    static SessionResponse error(String message) {
      return new SessionResponse("IDLE", null, null, null, null, null, null, null, message);
    }
  }
}

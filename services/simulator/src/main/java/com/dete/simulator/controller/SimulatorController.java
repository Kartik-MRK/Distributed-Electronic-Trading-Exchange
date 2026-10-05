package com.dete.simulator.controller;

import com.dete.simulator.config.SimulatorProperties;
import com.dete.simulator.service.BotAccountManager;
import com.dete.simulator.service.SimulatorService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/simulator")
public class SimulatorController {

  private final SimulatorService simulatorService;
  private final SimulatorProperties properties;
  private final BotAccountManager accountManager;

  public SimulatorController(
      SimulatorService simulatorService,
      SimulatorProperties properties,
      BotAccountManager accountManager) {
    this.simulatorService = simulatorService;
    this.properties = properties;
    this.accountManager = accountManager;
  }

  @GetMapping("/status")
  public ResponseEntity<Map<String, Object>> getStatus() {
    List<SimulatorService.BotStatusDto> statuses = simulatorService.getBotStatuses();
    return ResponseEntity.ok(
        Map.of(
            "enabled", properties.isEnabled(),
            "running", simulatorService.isRunning(),
            "initialized", simulatorService.isInitialized(),
            "bots", statuses));
  }

  @PostMapping("/start")
  public ResponseEntity<Map<String, Object>> startSimulator() {
    simulatorService.start();
    return ResponseEntity.ok(Map.of("message", "Simulator resumed", "running", true));
  }

  @PostMapping("/stop")
  public ResponseEntity<Map<String, Object>> stopSimulator() {
    simulatorService.stop();
    return ResponseEntity.ok(Map.of("message", "Simulator paused", "running", false));
  }

  @PostMapping("/seed")
  public ResponseEntity<Map<String, Object>> seedAccounts() {
    accountManager.initialize();
    return ResponseEntity.ok(Map.of("message", "Demo and bot accounts seeded successfully"));
  }
}

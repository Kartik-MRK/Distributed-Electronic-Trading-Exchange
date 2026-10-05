package com.dete.risk.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/** Manages the gRPC Server lifecycle within Spring Boot. */
@Component
public class RiskGrpcServer implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(RiskGrpcServer.class);

  private final int port;
  private final RiskGrpcService riskGrpcService;
  private final AtomicBoolean running = new AtomicBoolean(false);

  private Server server;

  public RiskGrpcServer(
      @Value("${risk.grpc.port:9095}") int port, RiskGrpcService riskGrpcService) {
    this.port = port;
    this.riskGrpcService = riskGrpcService;
  }

  @Override
  public void start() {
    if (running.compareAndSet(false, true)) {
      try {
        server = ServerBuilder.forPort(port).addService(riskGrpcService).build().start();
        log.info("Risk Service gRPC server started on port {}", server.getPort());
      } catch (IOException e) {
        running.set(false);
        throw new IllegalStateException(
            "Failed to start Risk Service gRPC server on port " + port, e);
      }
    }
  }

  @Override
  public void stop() {
    if (running.compareAndSet(true, false) && server != null) {
      log.info("Shutting down Risk Service gRPC server...");
      server.shutdown();
      try {
        if (!server.awaitTermination(5, TimeUnit.SECONDS)) {
          server.shutdownNow();
        }
      } catch (InterruptedException e) {
        server.shutdownNow();
        Thread.currentThread().interrupt();
      }
      log.info("Risk Service gRPC server stopped.");
    }
  }

  @Override
  public boolean isRunning() {
    return running.get();
  }

  public int getPort() {
    return server != null ? server.getPort() : port;
  }
}

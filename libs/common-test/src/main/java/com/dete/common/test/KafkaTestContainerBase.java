package com.dete.common.test;

import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Abstract base class for integration tests that need a real Kafka broker. */
@Testcontainers
public abstract class KafkaTestContainerBase {

  @Container
  @SuppressWarnings("resource")
  protected static final KafkaContainer KAFKA =
      new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.2")).withReuse(true);

  static {
    KAFKA.start();
    System.setProperty("spring.kafka.bootstrap-servers", KAFKA.getBootstrapServers());
  }
}

package com.dete.common.test;

import com.redis.testcontainers.RedisContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Full-stack test base spinning up Postgres + Kafka + Redis together. Use for end-to-end
 * integration tests that require the full infrastructure.
 */
@Testcontainers
public abstract class FullStackTestBase {

  @Container
  @SuppressWarnings("resource")
  protected static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("dete_test")
          .withUsername("dete")
          .withPassword("dete_test_secret")
          .withReuse(true);

  @Container
  @SuppressWarnings("resource")
  protected static final KafkaContainer KAFKA =
      new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1")).withReuse(true);

  @Container
  @SuppressWarnings("resource")
  protected static final RedisContainer REDIS =
      new RedisContainer(DockerImageName.parse("redis:7-alpine")).withReuse(true);

  static {
    POSTGRES.start();
    KAFKA.start();
    REDIS.start();
  }

  @DynamicPropertySource
  static void registerDynamicProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
    registry.add("spring.flyway.user", POSTGRES::getUsername);
    registry.add("spring.flyway.password", POSTGRES::getPassword);
    registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> String.valueOf(REDIS.getFirstMappedPort()));
    registry.add("spring.data.redis.password", () -> "");
  }
}

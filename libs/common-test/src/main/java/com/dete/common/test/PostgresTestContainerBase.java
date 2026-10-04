package com.dete.common.test;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Abstract base class for integration tests that need a real PostgreSQL instance. Subclasses
 * inherit a running, reusable Postgres container.
 *
 * <p>Usage: @SpringBootTest class MyServiceTest extends PostgresTestContainerBase { ... }
 */
@Testcontainers
public abstract class PostgresTestContainerBase {

  @Container
  @SuppressWarnings("resource")
  protected static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("dete_test")
          .withUsername("dete")
          .withPassword("dete_test_secret")
          .withReuse(true);

  static {
    POSTGRES.start();
    // Expose as system properties so Spring datasource auto-configuration picks them up
    System.setProperty("spring.datasource.url", POSTGRES.getJdbcUrl());
    System.setProperty("spring.datasource.username", POSTGRES.getUsername());
    System.setProperty("spring.datasource.password", POSTGRES.getPassword());
    System.setProperty("spring.flyway.url", POSTGRES.getJdbcUrl());
    System.setProperty("spring.flyway.user", POSTGRES.getUsername());
    System.setProperty("spring.flyway.password", POSTGRES.getPassword());
  }
}

package com.dete.common.test;

import com.redis.testcontainers.RedisContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Abstract base class for integration tests that need a real Redis instance. */
@Testcontainers
public abstract class RedisTestContainerBase {

  @Container
  @SuppressWarnings("resource")
  protected static final RedisContainer REDIS =
      new RedisContainer(DockerImageName.parse("redis:7-alpine")).withReuse(true);

  static {
    REDIS.start();
    System.setProperty("spring.data.redis.host", REDIS.getHost());
    System.setProperty("spring.data.redis.port", String.valueOf(REDIS.getFirstMappedPort()));
  }
}

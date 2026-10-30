package com.dete.simulation.control;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfiguration(after = DataSourceAutoConfiguration.class)
public class SimulationControlConfiguration {
  @Bean
  SimulationControlRepository simulationControlRepository(
      JdbcTemplate jdbc, PlatformTransactionManager tx) {
    return new SimulationControlRepository(jdbc, new TransactionTemplate(tx));
  }
}

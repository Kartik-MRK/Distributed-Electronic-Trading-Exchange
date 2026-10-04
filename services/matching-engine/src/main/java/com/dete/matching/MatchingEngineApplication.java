package com.dete.matching;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class MatchingEngineApplication {

  public static void main(String[] args) {
    SpringApplication.run(MatchingEngineApplication.class, args);
  }
}

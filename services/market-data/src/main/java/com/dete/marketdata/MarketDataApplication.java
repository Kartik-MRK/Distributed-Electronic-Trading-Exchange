package com.dete.marketdata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class MarketDataApplication {

  public static void main(String[] args) {
    SpringApplication.run(MarketDataApplication.class, args);
  }
}

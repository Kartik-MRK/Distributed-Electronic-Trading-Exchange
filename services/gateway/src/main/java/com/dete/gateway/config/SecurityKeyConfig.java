package com.dete.gateway.config;

import com.dete.common.security.JwtTokenValidator;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityKeyConfig {

  @Bean
  @ConditionalOnMissingBean
  public PublicKey rsaPublicKey() {
    try {
      KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
      gen.initialize(2048);
      return gen.generateKeyPair().getPublic();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to initialize RSA public key", e);
    }
  }

  @Bean
  @ConditionalOnMissingBean
  public JwtTokenValidator jwtTokenValidator(PublicKey rsaPublicKey) {
    return new JwtTokenValidator(rsaPublicKey);
  }
}

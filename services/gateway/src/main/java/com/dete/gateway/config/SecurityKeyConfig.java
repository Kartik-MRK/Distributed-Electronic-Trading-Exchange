package com.dete.gateway.config;

import com.dete.common.security.JwtTokenValidator;
import java.security.PublicKey;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityKeyConfig {

  @Bean
  @ConditionalOnMissingBean
  public PublicKey rsaPublicKey(
      @org.springframework.beans.factory.annotation.Value("${security.jwt.public-key:}")
          String publicKeyB64) {
    if (publicKeyB64 != null && !publicKeyB64.isBlank()) {
      return com.dete.common.security.RsaKeyUtils.parsePublicKey(publicKeyB64);
    }
    return com.dete.common.security.RsaKeyUtils.parsePublicKey(
        com.dete.common.security.RsaKeyUtils.DEFAULT_PUBLIC_KEY);
  }

  @Bean
  @ConditionalOnMissingBean
  public JwtTokenValidator jwtTokenValidator(PublicKey rsaPublicKey) {
    return new JwtTokenValidator(rsaPublicKey);
  }
}

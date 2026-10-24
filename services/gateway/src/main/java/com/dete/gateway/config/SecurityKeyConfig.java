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
          String publicKeyB64,
      @org.springframework.beans.factory.annotation.Value(
              "${security.jwt.allow-development-key:false}")
          boolean allowDevelopmentKey) {
    return com.dete.common.security.RsaKeyUtils.configuredPublicKey(
        publicKeyB64, allowDevelopmentKey);
  }

  @Bean
  @ConditionalOnMissingBean
  public JwtTokenValidator jwtTokenValidator(PublicKey rsaPublicKey) {
    return new JwtTokenValidator(rsaPublicKey);
  }
}

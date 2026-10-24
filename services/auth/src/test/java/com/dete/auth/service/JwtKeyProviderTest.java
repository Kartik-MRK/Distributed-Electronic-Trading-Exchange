package com.dete.auth.service;

import static org.assertj.core.api.Assertions.*;

import com.dete.common.security.RsaKeyUtils;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class JwtKeyProviderTest {
  @Test
  void missingOrDevelopmentKeysAreRejectedByDefault() {
    assertThatThrownBy(() -> new JwtKeyProvider("id", "", "", false))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> RsaKeyUtils.configuredPublicKey("", false))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                new JwtKeyProvider(
                    "id", RsaKeyUtils.DEFAULT_PRIVATE_KEY, RsaKeyUtils.DEFAULT_PUBLIC_KEY, false))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void configuredPairMustMatch() throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    var pair = generator.generateKeyPair();
    var other = generator.generateKeyPair();
    String privateKey = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
    String publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
    assertThat(new JwtKeyProvider("id", privateKey, publicKey, false).getPublicKey())
        .isEqualTo(pair.getPublic());
    assertThatThrownBy(
            () ->
                new JwtKeyProvider(
                    "id",
                    privateKey,
                    Base64.getEncoder().encodeToString(other.getPublic().getEncoded()),
                    false))
        .isInstanceOf(IllegalStateException.class);
  }
}

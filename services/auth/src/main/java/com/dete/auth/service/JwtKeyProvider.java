package com.dete.auth.service;

import com.dete.auth.dto.JwksResponse;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtKeyProvider {

  private final String keyId;
  private final KeyPair keyPair;

  public JwtKeyProvider(@Value("${auth.jwt.key-id:dete-auth-key-1}") String keyId) {
    this.keyId = keyId;
    try {
      KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
      keyGen.initialize(2048);
      this.keyPair = keyGen.generateKeyPair();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Failed to initialize RSA key pair generator", e);
    }
  }

  public String getKeyId() {
    return keyId;
  }

  public PublicKey getPublicKey() {
    return keyPair.getPublic();
  }

  public PrivateKey getPrivateKey() {
    return keyPair.getPrivate();
  }

  public JwksResponse getJwksResponse() {
    RSAPublicKey rsaPubKey = (RSAPublicKey) keyPair.getPublic();
    Map<String, Object> jwk =
        Map.of(
            "kty",
            "RSA",
            "use",
            "sig",
            "alg",
            "RS256",
            "kid",
            keyId,
            "n",
            toBase64Url(rsaPubKey.getModulus()),
            "e",
            toBase64Url(rsaPubKey.getPublicExponent()));
    return new JwksResponse(List.of(jwk));
  }

  private static String toBase64Url(BigInteger bigInt) {
    byte[] bytes = bigInt.toByteArray();
    if (bytes.length > 0 && bytes[0] == 0) {
      bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
    }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}

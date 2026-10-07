package com.dete.auth.service;

import com.dete.auth.dto.JwksResponse;
import com.dete.common.security.RsaKeyUtils;
import java.math.BigInteger;
import java.security.KeyPair;
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

  public JwtKeyProvider(String keyId) {
    this(keyId, null, null);
  }

  public JwtKeyProvider(
      @Value("${auth.jwt.key-id:dete-auth-key-1}") String keyId,
      @Value("${auth.jwt.private-key:}") String privateKeyB64,
      @Value("${auth.jwt.public-key:}") String publicKeyB64) {
    this.keyId = keyId;
    if (privateKeyB64 != null
        && !privateKeyB64.isBlank()
        && publicKeyB64 != null
        && !publicKeyB64.isBlank()) {
      PrivateKey priv = RsaKeyUtils.parsePrivateKey(privateKeyB64);
      PublicKey pub = RsaKeyUtils.parsePublicKey(publicKeyB64);
      this.keyPair = new KeyPair(pub, priv);
    } else {
      PrivateKey priv = RsaKeyUtils.parsePrivateKey(RsaKeyUtils.DEFAULT_PRIVATE_KEY);
      PublicKey pub = RsaKeyUtils.parsePublicKey(RsaKeyUtils.DEFAULT_PUBLIC_KEY);
      this.keyPair = new KeyPair(pub, priv);
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

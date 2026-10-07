package com.dete.common.security;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Utility for parsing and providing standard RS256 keys across DETE microservices. */
public final class RsaKeyUtils {

  public static final String DEFAULT_PUBLIC_KEY =
      "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA63M1Lw640B5WuINW7DVYd40TfuExpaG5sihggnkcvIJcNE13"
          + "txCy/RUJVpnjJDwWdGWdf5uJbrDI8xA24F/of0e+xruKAw8d58DnmYvxqM+EggvsXdoNOPx+qtT+v2MmMIJNXwAoD6"
          + "+JrWKq2t1v+taLEU2ME5zX/VTlRs9QaGgxr7XyEw4hiSx1Rwt24wDUydvSMhdI6sJ7VD9UluhhDtH98NCO2xggmTQ"
          + "Vu4UfuCkzKLyQ/pwra+pklH+B4O/j90+SGQfjAzUn7MWP9sw0k7vMP526n248Fev61jQpvvus3XBuLoAYEIt7yoQU"
          + "5AAPtKFtTKcf4q8SfrVCTu1LjQIDAQAB";

  public static final String DEFAULT_PRIVATE_KEY =
      "MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQDrczUvDrjQHla4g1bsNVh3jRN+4TGlobmyKGCCeR"
          + "y8glw0TXe3ELL9FQlWmeMkPBZ0ZZ1/m4lusMjzEDbgX+h/R77Gu4oDDx3nwOeZi/Goz4SCC+xd2g04/H6q1P6/Yy"
          + "Ywgk1fACgPr4mtYqra3W/61osRTYwTnNf9VOVGz1BoaDGvtfITDiGJLHVHC3bjANTJ29IyF0jqwntUP1SW6GEO0f"
          + "3w0I7bGCCZNBW7hR+4KTMovJD+nCtr6mSUf4Hg7+P3T5IZB+MDNSfsxY/2zDSTu8w/nbqfbjwV6/rWNCm++6zdcG"
          + "4ugBgQi3vKhBTkAA+0oW1Mpx/irxJ+tUJO7UuNAgMBAAECgf828+y1zvnTmUEkT4M6HjlHreWe93Be+CAfNM1Rpn"
          + "LOjJ54lpwaXgtohkBntgMNsfiLsivQXLz22MwN7vrf44KgMl6Ug7DyhdS1ApELhV4hyE1lDONeBEaRxlzKAmxyvv"
          + "Fq4P6t7ZtsDcxUa3L1IjnAkdJjd3X6HeFLf0UEAd8Cftx8yoUObWSWtcckqW2cUv7z1f0AzNmyQluaMLLljTV8f2"
          + "x82vRhFjjOAuUSXChamgsTn/EYwH1T83yzUU/75c/36ipVyW1QALsKbcaYB9C0eqXz8AV9SzhXDchwOuyEZ02l11"
          + "Lf9SsvPKMxYj06VWEFnuazXwbSa9C4v2+SyPkCgYEA+NVL8jItnHKyaNvoxYRJ9jhenWGQhNAUSL8ERa47XtcEw+"
          + "15mcdkKFBxp0amJ0KaBk115NeCovgAqaXQ4hJ9+ZckmDMXFBWsmo7395uEp8UwltjEhgqUoiDkdCw2RGUiWxJlzt"
          + "7W5E+iHEjY9SJ+e1ae8YT03I9jOUZvhoKxQyUCgYEA8js76PeY+LN91VkvPZd5W8ax8gXrAj6hHK67X/E+OtNNE"
          + "4PALNeK1WbStvAHww6w0h4Jif+1jGUr3I8d92sALVv7RfL/wJPb6l4w7ODt9ukhy6a/m7j9H0ZVMQHEpRgtovqfo"
          + "ZlvLLetFNQN9YDtTkq7DBOmk+dV+L3kqDZmrkkCgYEAyJNOW5Uc9P3zV9jjocsHv2QiyCOr1ZLQtu7aZ7yQ+NrDa"
          + "ZdTrKRv1Js7ccXeCsmB1FC+Fk+tauYTyqvxWznit7ygb9rG4Ja0AgX6VWnTnVSy3PUwPIfs1V9EJ2y39Zu7/MMws"
          + "3cmqvSRzNDfqYE7iPoCttYgdmVSawmevkM81c0CgYEAoQJyfhYmHhng6HFRj1UiG7jlCwSYA3Pxz4jtj4ZhYOSW"
          + "9QO8N5JF/DdOlL7Tyfn32pKQsKPB30JYd1DCEHWVNpFPYl11VmJx6UUWrD2Or3KjPiTmc/HwfltXwS11sm9x1kV"
          + "U2iuM1GJ6r7+MT8WU91eBITPmo6U/ZgmC9bTDZ9ECgYArjFcS8cg9M8HAKWvgWuPwHYMobxXSK9wsjSTmMNkdwH"
          + "/VwZseXonzsu7su4FV7hWwYpujZY0UaZpa8bwdX1O9BUwg2qvhVTfFHGFW1vWnrvwAfolGl0gzfRckjCq3HIdBr"
          + "w4U3L5QAoFQ+kRes1FsKa7U1dBAB34MVLdj2n0c/Q==";

  private RsaKeyUtils() {}

  public static PublicKey parsePublicKey(String b64) {
    try {
      String clean = cleanKeyString(b64);
      byte[] bytes = Base64.getDecoder().decode(clean);
      return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes));
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to parse RSA public key", e);
    }
  }

  public static PrivateKey parsePrivateKey(String b64) {
    try {
      String clean = cleanKeyString(b64);
      byte[] bytes = Base64.getDecoder().decode(clean);
      return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(bytes));
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to parse RSA private key", e);
    }
  }

  private static String cleanKeyString(String key) {
    return key.replace("-----BEGIN PUBLIC KEY-----", "")
        .replace("-----END PUBLIC KEY-----", "")
        .replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")
        .replaceAll("\\s+", "");
  }
}

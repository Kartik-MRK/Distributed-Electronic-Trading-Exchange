package com.dete.account.model;

import java.util.UUID;

public record Balance(UUID accountId, String asset, long available, long reserved) {

  public long total() {
    return available + reserved;
  }
}

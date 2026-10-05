package com.dete.order.client;

import java.util.UUID;

public interface AccountClient {

  boolean reserveFunds(UUID accountId, UUID orderId, String asset, long amount);

  boolean releaseFunds(UUID accountId, UUID orderId, String asset, long amount);
}

package com.dete.common.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Base contract for all Kafka events published in DETE. Every event MUST carry these fields for
 * idempotency and tracing.
 */
public interface DeteEvent {
  /** Unique event identifier. Used for idempotency deduplication on consumers. */
  UUID eventId();

  /** String discriminator for deserialization and audit tagging. */
  String eventType();

  /** Wall-clock time when the event was created. */
  Instant timestamp();

  /** Schema version for forward-compatibility. */
  int version();
}

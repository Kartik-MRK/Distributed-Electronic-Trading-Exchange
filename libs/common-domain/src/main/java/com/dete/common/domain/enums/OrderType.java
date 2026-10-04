package com.dete.common.domain.enums;

/** Order execution type. Determines matching and resting behavior. */
public enum OrderType {
  /** Rests in book at specified price. GTC by default. */
  LIMIT,
  /** Executes immediately at best available price. No resting. */
  MARKET,
  /** Immediate-or-Cancel: fills what it can immediately, cancels remainder. */
  IOC,
  /** Fill-or-Kill: fills completely or is cancelled entirely — no partial fill. */
  FOK,
  /** Good-Till-Cancelled: like LIMIT but explicitly marked as persistent. */
  GTC;

  public boolean isRestingAllowed() {
    return this == LIMIT || this == GTC;
  }

  public boolean isImmediateOnly() {
    return this == MARKET || this == IOC || this == FOK;
  }
}

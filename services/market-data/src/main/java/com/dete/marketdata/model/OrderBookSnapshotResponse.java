package com.dete.marketdata.model;

import com.dete.common.domain.enums.Instrument;
import java.time.Instant;
import java.util.List;

/** L2 Order Book Snapshot response model. */
public record OrderBookSnapshotResponse(
    Instrument instrument,
    long sequenceNumber,
    Instant timestamp,
    List<PriceLevelView> bids,
    List<PriceLevelView> asks) {}

package com.dete.matching.engine.model;

import com.dete.common.domain.enums.Instrument;
import java.util.List;

/** Level 2 market data snapshot containing top-of-book bids and asks. */
public record L2Depth(
    Instrument instrument, long sequenceNumber, List<L2Level> bids, List<L2Level> asks) {

  public record L2Level(long price, long volume, int orderCount) {}
}

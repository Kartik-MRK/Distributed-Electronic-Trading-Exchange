package com.dete.marketdata.model;

/** Level 2 aggregated price level view for bids and asks. */
public record PriceLevelView(long price, long volume, int orderCount) {}

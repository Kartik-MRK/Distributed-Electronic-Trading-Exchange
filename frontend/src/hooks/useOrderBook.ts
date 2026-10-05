'use client';

import { useState, useEffect, useRef } from 'react';
import { Instrument, OrderBookData, PriceLevel } from '../types';
import { useWebSocket } from './useWebSocket';
import { INSTRUMENT_METAS } from '../lib/api';

function generateInitialLadder(instrument: Instrument): { bids: PriceLevel[]; asks: PriceLevel[] } {
  const meta = INSTRUMENT_METAS[instrument];
  const mid = parseFloat(meta.lastPrice);
  const step = mid * 0.00035;

  const bids: PriceLevel[] = [];
  const asks: PriceLevel[] = [];

  let cumBidQty = 0;
  for (let i = 0; i < 15; i++) {
    const p = (mid - step * (i + 1)).toFixed(meta.priceDecimals);
    const q = (0.2 + (i * 0.15) + Math.random() * 0.4).toFixed(meta.quantityDecimals);
    cumBidQty += parseFloat(q);
    bids.push({ price: p, quantity: q, total: cumBidQty.toFixed(meta.quantityDecimals) });
  }

  let cumAskQty = 0;
  for (let i = 0; i < 15; i++) {
    const p = (mid + step * (i + 1)).toFixed(meta.priceDecimals);
    const q = (0.2 + (i * 0.15) + Math.random() * 0.4).toFixed(meta.quantityDecimals);
    cumAskQty += parseFloat(q);
    asks.push({ price: p, quantity: q, total: cumAskQty.toFixed(meta.quantityDecimals) });
  }

  // Calculate depth percentages
  const maxTotal = Math.max(cumBidQty, cumAskQty);
  bids.forEach((b) => {
    b.depthPercent = Math.min(100, (parseFloat(b.total || '0') / maxTotal) * 100);
  });
  asks.forEach((a) => {
    a.depthPercent = Math.min(100, (parseFloat(a.total || '0') / maxTotal) * 100);
  });

  return { bids, asks };
}

export function useOrderBook(instrument: Instrument) {
  const { subscribe } = useWebSocket();
  const [orderBook, setOrderBook] = useState<OrderBookData>(() => {
    const ladder = generateInitialLadder(instrument);
    const bestBid = parseFloat(ladder.bids[0].price);
    const bestAsk = parseFloat(ladder.asks[0].price);
    const spread = (bestAsk - bestBid).toFixed(2);
    const spreadPercent = ((parseFloat(spread) / bestAsk) * 100).toFixed(3);

    return {
      instrument,
      sequenceNumber: 1,
      bids: ladder.bids,
      asks: ladder.asks,
      spread,
      spreadPercent,
    };
  });

  const [flashedLevels, setFlashedLevels] = useState<Record<string, 'up' | 'down'>>({});
  const prevBookRef = useRef<OrderBookData>(orderBook);

  // Re-initialize when instrument changes
  useEffect(() => {
    const ladder = generateInitialLadder(instrument);
    const bestBid = parseFloat(ladder.bids[0].price);
    const bestAsk = parseFloat(ladder.asks[0].price);
    const spread = (bestAsk - bestBid).toFixed(2);
    const spreadPercent = ((parseFloat(spread) / bestAsk) * 100).toFixed(3);

    const initial = {
      instrument,
      sequenceNumber: 1,
      bids: ladder.bids,
      asks: ladder.asks,
      spread,
      spreadPercent,
    };
    setOrderBook(initial);
    prevBookRef.current = initial;
  }, [instrument]);

  // Subscribe to real WebSocket orderbook updates
  useEffect(() => {
    const topic = `/topic/orderbook.${instrument}`;
    const unsubscribe = subscribe<OrderBookData>(topic, (incoming) => {
      if (!incoming || !incoming.bids || !incoming.asks) return;

      // Track level flashes by comparing to prevBookRef
      const flashes: Record<string, 'up' | 'down'> = {};
      const prevBids = prevBookRef.current.bids;
      incoming.bids.slice(0, 15).forEach((newBid) => {
        const match = prevBids.find((p) => p.price === newBid.price);
        if (match && match.quantity !== newBid.quantity) {
          flashes[newBid.price] = parseFloat(newBid.quantity) > parseFloat(match.quantity) ? 'up' : 'down';
        }
      });

      if (Object.keys(flashes).length > 0) {
        setFlashedLevels(flashes);
        setTimeout(() => setFlashedLevels({}), 600);
      }

      // Compute cumulative totals and depth percentages
      let bidTotal = 0;
      const computedBids = incoming.bids.slice(0, 15).map((b) => {
        bidTotal += parseFloat(b.quantity);
        return { ...b, total: bidTotal.toFixed(4) };
      });

      let askTotal = 0;
      const computedAsks = incoming.asks.slice(0, 15).map((a) => {
        askTotal += parseFloat(a.quantity);
        return { ...a, total: askTotal.toFixed(4) };
      });

      const maxTotal = Math.max(bidTotal, askTotal) || 1;
      computedBids.forEach((b) => {
        b.depthPercent = Math.min(100, (parseFloat(b.total || '0') / maxTotal) * 100);
      });
      computedAsks.forEach((a) => {
        a.depthPercent = Math.min(100, (parseFloat(a.total || '0') / maxTotal) * 100);
      });

      const bestBid = computedBids.length > 0 ? parseFloat(computedBids[0].price) : 0;
      const bestAsk = computedAsks.length > 0 ? parseFloat(computedAsks[0].price) : 0;
      const spreadVal = bestAsk > bestBid ? (bestAsk - bestBid).toFixed(2) : '0.00';
      const spreadPct = bestAsk > 0 ? ((parseFloat(spreadVal) / bestAsk) * 100).toFixed(3) : '0.000';

      const updated: OrderBookData = {
        instrument,
        sequenceNumber: incoming.sequenceNumber,
        bids: computedBids,
        asks: computedAsks,
        spread: spreadVal,
        spreadPercent: spreadPct,
      };

      prevBookRef.current = updated;
      setOrderBook(updated);
    });

    return () => {
      unsubscribe();
    };
  }, [instrument, subscribe]);

  // Subtle simulated micro-movements to keep ladder active if no server feed is streaming
  useEffect(() => {
    const interval = setInterval(() => {
      setOrderBook((prev) => {
        if (!prev.bids.length || !prev.asks.length) return prev;
        const targetIdx = Math.floor(Math.random() * 5);
        const isBid = Math.random() > 0.5;
        const newBids = [...prev.bids];
        const newAsks = [...prev.asks];
        const flashes: Record<string, 'up' | 'down'> = {};

        if (isBid && newBids[targetIdx]) {
          const oldQ = parseFloat(newBids[targetIdx].quantity);
          const delta = (Math.random() - 0.48) * 0.25;
          const newQ = Math.max(0.05, oldQ + delta).toFixed(INSTRUMENT_METAS[instrument].quantityDecimals);
          flashes[newBids[targetIdx].price] = delta >= 0 ? 'up' : 'down';
          newBids[targetIdx] = { ...newBids[targetIdx], quantity: newQ };
        } else if (newAsks[targetIdx]) {
          const oldQ = parseFloat(newAsks[targetIdx].quantity);
          const delta = (Math.random() - 0.48) * 0.25;
          const newQ = Math.max(0.05, oldQ + delta).toFixed(INSTRUMENT_METAS[instrument].quantityDecimals);
          flashes[newAsks[targetIdx].price] = delta >= 0 ? 'up' : 'down';
          newAsks[targetIdx] = { ...newAsks[targetIdx], quantity: newQ };
        }

        setFlashedLevels(flashes);
        setTimeout(() => setFlashedLevels({}), 500);

        return { ...prev, bids: newBids, asks: newAsks };
      });
    }, 3000);

    return () => clearInterval(interval);
  }, [instrument]);

  return {
    orderBook,
    flashedLevels,
  };
}

'use client';

import { useState, useEffect, useRef } from 'react';
import { Instrument, OrderBookData, PriceLevel } from '../types';
import { useWebSocket } from './useWebSocket';
import { api, INSTRUMENT_METAS } from '../lib/api';
import { toBackendInstrument } from '../lib/format';

function emptyBook(instrument: Instrument): OrderBookData {
  return { instrument, sequenceNumber: 0, bids: [], asks: [], spread: '0.00', spreadPercent: '0.000' };
}

export function useOrderBook(instrument: Instrument) {
  const { subscribe } = useWebSocket();
  const [orderBook, setOrderBook] = useState<OrderBookData>(() => emptyBook(instrument));

  const [flashedLevels, setFlashedLevels] = useState<Record<string, 'up' | 'down'>>({});
  const prevBookRef = useRef<OrderBookData>(orderBook);

  // Re-initialize when instrument changes
  useEffect(() => {
    const initial = emptyBook(instrument);
    setOrderBook(initial);
    prevBookRef.current = initial;
  }, [instrument]);

  // Subscribe to real WebSocket orderbook updates
  useEffect(() => {
    const backendInst = toBackendInstrument(instrument);
    const primaryTopic = `/topic/orderbook.${backendInst}`;
    const altTopic = `/topic/orderbook.${instrument}`;

    let active = true;
    const handleIncomingBook = (incoming: Record<string, unknown>) => {
      if (!active || !incoming) return;
      const rawBids = incoming.bids;
      const rawAsks = incoming.asks;
      if (!Array.isArray(rawBids) || !Array.isArray(rawAsks)) return;

      const meta = INSTRUMENT_METAS[instrument];

      // Compute cumulative totals and depth percentages
      let bidTotal = 0;
      const computedBids = rawBids.slice(0, 15).map((bRecord: unknown) => {
        const b = bRecord as Record<string, unknown>;
        const rawP = b.price;
        const pNum =
          Number(rawP) / 100_000_000;
        const rawQ = b.volume ?? b.quantity ?? 0;
        const qNum =
          Number(rawQ) / 100_000_000;
        bidTotal += qNum;
        return {
          price: pNum.toFixed(meta.priceDecimals),
          quantity: qNum.toFixed(meta.quantityDecimals),
          total: bidTotal.toFixed(meta.quantityDecimals),
        };
      });

      let askTotal = 0;
      const computedAsks = rawAsks.slice(0, 15).map((aRecord: unknown) => {
        const a = aRecord as Record<string, unknown>;
        const rawP = a.price;
        const pNum =
          Number(rawP) / 100_000_000;
        const rawQ = a.volume ?? a.quantity ?? 0;
        const qNum =
          Number(rawQ) / 100_000_000;
        askTotal += qNum;
        return {
          price: pNum.toFixed(meta.priceDecimals),
          quantity: qNum.toFixed(meta.quantityDecimals),
          total: askTotal.toFixed(meta.quantityDecimals),
        };
      });

      // Track level flashes by comparing to prevBookRef
      const flashes: Record<string, 'up' | 'down'> = {};
      const prevBids = prevBookRef.current.bids;
      computedBids.forEach((newBid: PriceLevel) => {
        const match = prevBids.find((p) => p.price === newBid.price);
        if (match && match.quantity !== newBid.quantity) {
          flashes[newBid.price] =
            parseFloat(newBid.quantity) > parseFloat(match.quantity) ? 'up' : 'down';
        }
      });

      if (Object.keys(flashes).length > 0) {
        setFlashedLevels(flashes);
        setTimeout(() => setFlashedLevels({}), 600);
      }

      const maxTotal = Math.max(bidTotal, askTotal) || 1;
      computedBids.forEach((b: PriceLevel) => {
        b.depthPercent = Math.min(100, (parseFloat(b.total || '0') / maxTotal) * 100);
      });
      computedAsks.forEach((a: PriceLevel) => {
        a.depthPercent = Math.min(100, (parseFloat(a.total || '0') / maxTotal) * 100);
      });

      const bestBid = computedBids.length > 0 ? parseFloat(computedBids[0].price) : 0;
      const bestAsk = computedAsks.length > 0 ? parseFloat(computedAsks[0].price) : 0;
      const spreadVal = bestAsk > bestBid ? (bestAsk - bestBid).toFixed(2) : '0.00';
      const spreadPct =
        bestAsk > 0 ? ((parseFloat(spreadVal) / bestAsk) * 100).toFixed(3) : '0.000';

      const updated: OrderBookData = {
        instrument,
        sequenceNumber: (incoming.sequenceNumber as number) || 1,
        bids: computedBids,
        asks: computedAsks,
        spread: spreadVal,
        spreadPercent: spreadPct,
      };

      prevBookRef.current = updated;
      setOrderBook(updated);
    };

    const unsubscribePrimary = subscribe<Record<string, unknown>>(
      primaryTopic,
      handleIncomingBook
    );
    const unsubscribeAlt =
      primaryTopic !== altTopic
        ? subscribe<Record<string, unknown>>(altTopic, handleIncomingBook)
        : () => {};

    void api.marketData.getOrderBook(instrument).then((book) => {
      if (prevBookRef.current.sequenceNumber === 0) handleIncomingBook(book as unknown as Record<string, unknown>);
    }).catch(() => { /* Keep empty depth until a snapshot arrives. */ });

    return () => {
      active = false;
      unsubscribePrimary();
      unsubscribeAlt();
    };
  }, [instrument, subscribe]);

  return {
    orderBook,
    flashedLevels,
  };
}

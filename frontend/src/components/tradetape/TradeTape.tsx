'use client';

import React, { useState, useEffect } from 'react';
import { Instrument, TradeRecord } from '../../types';
import { useWebSocket } from '../../hooks/useWebSocket';
import { api, INSTRUMENT_METAS } from '../../lib/api';

import { toBackendInstrument } from '../../lib/format';

interface TradeTapeProps {
  instrument: Instrument;
}

export function TradeTape({ instrument }: TradeTapeProps) {
  const { subscribe, connectionState } = useWebSocket();
  const [trades, setTrades] = useState<TradeRecord[]>([]);

  // Reset when instrument changes
  useEffect(() => {
    setTrades([]);
    let active = true;
    void api.marketData.getRecentTrades(instrument).then((history) => {
      if (active) setTrades((live) => [...live, ...history.filter((t) => !live.some((l) => l.tradeId === t.tradeId))].slice(0, 50));
    }).catch(() => {});
    return () => { active = false; };
  }, [instrument]);

  // Subscribe to live trades
  useEffect(() => {
    const backendInst = toBackendInstrument(instrument);
    const primaryTopic = `/topic/trades.${backendInst}`;
    const altTopic = `/topic/trades.${instrument}`;

    const handleIncomingTrade = (incoming: Record<string, unknown>) => {
      if (!incoming) return;
      const meta = INSTRUMENT_METAS[instrument];
      const rawP = incoming.price;
      const pNum =
        Number(rawP) / 100_000_000;
      const rawQ = incoming.quantity;
      const qNum =
        Number(rawQ || 0) / 100_000_000;

      const record: TradeRecord = {
        tradeId: String(incoming.tradeId || incoming.eventId || `tr-${Date.now()}`),
        instrument,
        price: pNum.toFixed(meta.priceDecimals),
        quantity: qNum.toFixed(meta.quantityDecimals),
        makerSide: (incoming.makerSide as 'BUY' | 'SELL') || (incoming.side === 'BUY' ? 'SELL' : 'BUY'),
        takerSide: (incoming.takerSide as 'BUY' | 'SELL') || (incoming.side as 'BUY' | 'SELL') || 'BUY',
        executedAt: (incoming.timestamp as string) || (incoming.executedAt as string) || new Date().toISOString(),
      };

      setTrades((prev) => [record, ...prev.filter((trade) => trade.tradeId !== record.tradeId).slice(0, 49)]);
    };

    const unsubscribePrimary = subscribe<Record<string, unknown>>(
      primaryTopic,
      handleIncomingTrade
    );
    const unsubscribeAlt =
      primaryTopic !== altTopic
        ? subscribe<Record<string, unknown>>(altTopic, handleIncomingTrade)
        : () => {};

    return () => {
      unsubscribePrimary();
      unsubscribeAlt();
    };
  }, [instrument, subscribe]);

  return (
    <div className="trade-tape">
      <p className="panel-footnote">
        {connectionState === 'connected'
          ? 'Market executions'
          : 'Feed unavailable'}
      </p>
      <div className="depth-columns">
        <span>Price (USD)</span>
        <span>Size</span>
        <span>Time</span>
      </div>
      <div className="tape-list">
        {trades.map((trade) => (
          <div className="tape-row font-mono" key={trade.tradeId}>
            <span
              className={trade.takerSide === 'BUY' ? 'positive' : 'negative'}
            >
              {trade.price}
            </span>
            <span>{trade.quantity}</span>
            <span className="muted">
              {new Date(trade.executedAt).toLocaleTimeString('en-GB')}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

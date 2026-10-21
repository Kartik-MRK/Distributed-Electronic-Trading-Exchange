'use client';

import React, { useState, useEffect } from 'react';
import { Instrument, TradeRecord } from '../../types';
import { useWebSocket } from '../../hooks/useWebSocket';
import { INSTRUMENT_METAS } from '../../lib/api';

import { toBackendInstrument } from '../../lib/format';

interface TradeTapeProps {
  instrument: Instrument;
}

function generateInitialTrades(instrument: Instrument): TradeRecord[] {
  const meta = INSTRUMENT_METAS[instrument];
  const mid = parseFloat(meta.lastPrice);
  const trades: TradeRecord[] = [];
  const now = Date.now();

  for (let i = 0; i < 35; i++) {
    const isBuy = Math.random() > 0.48;
    const offset = (Math.random() - 0.5) * (mid * 0.001);
    const p = (mid + offset).toFixed(meta.priceDecimals);
    const q = (0.01 + Math.random() * 0.85).toFixed(meta.quantityDecimals);
    const tradeTime = new Date(now - i * 1400).toISOString();

    trades.push({
      tradeId: `trade-seed-${i}`,
      instrument,
      price: p,
      quantity: q,
      makerSide: isBuy ? 'SELL' : 'BUY',
      takerSide: isBuy ? 'BUY' : 'SELL',
      executedAt: tradeTime,
    });
  }

  return trades;
}

export function TradeTape({ instrument }: TradeTapeProps) {
  const { subscribe, connectionState } = useWebSocket();
  const [trades, setTrades] = useState<TradeRecord[]>(() =>
    generateInitialTrades(instrument),
  );

  // Reset when instrument changes
  useEffect(() => {
    setTrades(generateInitialTrades(instrument));
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
        typeof rawP === 'number' && rawP >= 100_000_000
          ? rawP / 100_000_000
          : Number(rawP);
      const rawQ = incoming.quantity;
      const qNum =
        typeof rawQ === 'number' && rawQ >= 100_000_000
          ? rawQ / 100_000_000
          : Number(rawQ || 0);

      const record: TradeRecord = {
        tradeId: String(incoming.tradeId || incoming.eventId || `tr-${Date.now()}`),
        instrument,
        price: pNum.toFixed(meta.priceDecimals),
        quantity: qNum.toFixed(meta.quantityDecimals),
        makerSide: (incoming.makerSide as 'BUY' | 'SELL') || (incoming.side === 'BUY' ? 'SELL' : 'BUY'),
        takerSide: (incoming.takerSide as 'BUY' | 'SELL') || (incoming.side as 'BUY' | 'SELL') || 'BUY',
        executedAt: (incoming.timestamp as string) || (incoming.executedAt as string) || new Date().toISOString(),
      };

      setTrades((prev) => [record, ...prev.slice(0, 49)]);
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
          : 'Preview · Sample executions'}
      </p>
      <div className="depth-columns">
        <span>Price (USDT)</span>
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

'use client';

import React, { useState, useEffect } from 'react';
import { Instrument, TradeRecord } from '../../types';
import { useWebSocket } from '../../hooks/useWebSocket';
import { INSTRUMENT_METAS } from '../../lib/api';

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
    const topic = `/topic/trades.${instrument}`;
    const unsubscribe = subscribe<TradeRecord>(topic, (incoming) => {
      if (!incoming || !incoming.price) return;
      setTrades((prev) => [incoming, ...prev.slice(0, 49)]);
    });

    return () => {
      unsubscribe();
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

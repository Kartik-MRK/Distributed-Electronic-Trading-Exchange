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
  const { subscribe } = useWebSocket();
  const [trades, setTrades] = useState<TradeRecord[]>(() => generateInitialTrades(instrument));

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
    <div
      className="glass-panel"
      style={{
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        backgroundColor: 'var(--bg-panel)',
      }}
    >
      {/* Panel Header */}
      <div
        style={{
          padding: '8px 12px',
          borderBottom: '1px solid var(--border-subtle)',
          fontSize: '0.78rem',
          fontWeight: 700,
          color: 'var(--text-secondary)',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
        }}
      >
        <span>TRADE TAPE</span>
        <span style={{ fontSize: '0.68rem', color: 'var(--text-muted)' }}>Market Executions</span>
      </div>

      {/* Column Headers */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: '1fr 1fr 1fr',
          padding: '4px 10px',
          fontSize: '0.68rem',
          color: 'var(--text-muted)',
          borderBottom: '1px solid var(--border-subtle)',
        }}
      >
        <span>Price (USDT)</span>
        <span style={{ textAlign: 'right' }}>Size</span>
        <span style={{ textAlign: 'right' }}>Time</span>
      </div>

      {/* Trades Scrollable List */}
      <div
        style={{
          flex: 1,
          overflowY: 'auto',
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        {trades.map((t) => {
          const isBuy = t.takerSide === 'BUY';
          const timeStr = t.executedAt ? new Date(t.executedAt).toLocaleTimeString() : '--:--:--';

          return (
            <div
              key={t.tradeId}
              style={{
                display: 'grid',
                gridTemplateColumns: '1fr 1fr 1fr',
                padding: '3px 10px',
                fontSize: '0.72rem',
                borderBottom: '1px solid rgba(255,255,255,0.02)',
                alignItems: 'center',
              }}
            >
              <span
                className="font-mono"
                style={{
                  color: isBuy ? 'var(--bid-green)' : 'var(--ask-red)',
                  fontWeight: 600,
                }}
              >
                {t.price}
              </span>
              <span
                className="font-mono"
                style={{
                  color: 'var(--text-primary)',
                  textAlign: 'right',
                }}
              >
                {t.quantity}
              </span>
              <span
                className="font-mono"
                style={{
                  color: 'var(--text-muted)',
                  textAlign: 'right',
                  fontSize: '0.66rem',
                }}
              >
                {timeStr}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

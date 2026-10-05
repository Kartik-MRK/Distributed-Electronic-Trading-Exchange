'use client';

import React from 'react';
import { Instrument, PriceLevel } from '../../types';
import { useOrderBook } from '../../hooks/useOrderBook';
import { ArrowUp, ArrowDown } from 'lucide-react';

interface OrderBookProps {
  instrument: Instrument;
  onSelectPrice: (price: string) => void;
}

export function OrderBook({ instrument, onSelectPrice }: OrderBookProps) {
  const { orderBook, flashedLevels } = useOrderBook(instrument);

  // Render a price row with depth bar and click handler
  const renderRow = (level: PriceLevel, isBid: boolean) => {
    const flash = flashedLevels[level.price];
    const flashClass = flash === 'up' ? 'flash-up' : flash === 'down' ? 'flash-down' : '';
    const depthPct = level.depthPercent || 0;

    return (
      <div
        key={`${isBid ? 'b' : 'a'}-${level.price}`}
        className={`orderbook-row ${flashClass}`}
        onClick={() => onSelectPrice(level.price)}
        style={{
          display: 'grid',
          gridTemplateColumns: '1fr 1fr 1fr',
          padding: '2px 10px',
          fontSize: '0.72rem',
          position: 'relative',
          cursor: 'pointer',
          userSelect: 'none',
          height: 18,
          alignItems: 'center',
        }}
      >
        {/* Cumulative Depth Horizontal Bar */}
        <div
          style={{
            position: 'absolute',
            top: 0,
            bottom: 0,
            right: 0,
            width: `${Math.min(100, Math.max(0, depthPct))}%`,
            backgroundColor: isBid ? 'var(--bid-green-depth)' : 'var(--ask-red-depth)',
            zIndex: 1,
            pointerEvents: 'none',
            transition: 'width 0.15s ease',
          }}
        />

        {/* Price column */}
        <span
          className="font-mono"
          style={{
            color: isBid ? 'var(--bid-green)' : 'var(--ask-red)',
            fontWeight: 600,
            zIndex: 2,
          }}
        >
          {level.price}
        </span>

        {/* Quantity column */}
        <span
          className="font-mono"
          style={{
            color: 'var(--text-primary)',
            textAlign: 'right',
            zIndex: 2,
          }}
        >
          {level.quantity}
        </span>

        {/* Cumulative Total column */}
        <span
          className="font-mono"
          style={{
            color: 'var(--text-secondary)',
            textAlign: 'right',
            zIndex: 2,
          }}
        >
          {level.total}
        </span>
      </div>
    );
  };

  // Asks are displayed top down (highest down to lowest ask at the spread)
  const reversedAsks = [...orderBook.asks].reverse();

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
      {/* Panel Title */}
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
        <span>ORDER BOOK</span>
        <span style={{ fontSize: '0.68rem', color: 'var(--text-muted)' }}>Top 15 Levels</span>
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
        <span style={{ textAlign: 'right' }}>Total</span>
      </div>

      {/* Asks (Sells) */}
      <div
        style={{
          flex: 1,
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'flex-end',
          overflow: 'hidden',
        }}
      >
        {reversedAsks.map((ask) => renderRow(ask, false))}
      </div>

      {/* Spread Bar */}
      <div
        style={{
          padding: '6px 10px',
          margin: '2px 0',
          background: 'var(--bg-input)',
          borderTop: '1px solid var(--border-subtle)',
          borderBottom: '1px solid var(--border-subtle)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          fontSize: '0.75rem',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <span
            className="font-mono"
            style={{
              fontSize: '0.88rem',
              fontWeight: 700,
              color: 'var(--bid-green)',
            }}
          >
            {orderBook.bids[0]?.price || '---'}
          </span>
          <ArrowUp size={12} color="var(--bid-green)" />
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 6, color: 'var(--text-muted)', fontSize: '0.68rem' }}>
          <span>Spread</span>
          <span className="font-mono" style={{ color: 'var(--text-secondary)' }}>
            {orderBook.spread} ({orderBook.spreadPercent}%)
          </span>
        </div>
      </div>

      {/* Bids (Buys) */}
      <div
        style={{
          flex: 1,
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
        }}
      >
        {orderBook.bids.map((bid) => renderRow(bid, true))}
      </div>
    </div>
  );
}

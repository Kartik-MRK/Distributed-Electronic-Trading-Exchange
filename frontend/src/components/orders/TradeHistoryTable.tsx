'use client';

import React from 'react';
import { TradeRecord } from '../../types';
import { CheckCircle2 } from 'lucide-react';

interface TradeHistoryTableProps {
  trades: TradeRecord[];
}

export function TradeHistoryTable({ trades }: TradeHistoryTableProps) {
  if (trades.length === 0) {
    return (
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          height: '100%',
          color: 'var(--text-muted)',
          fontSize: '0.8rem',
          gap: 6,
        }}
      >
        <CheckCircle2 size={20} color="var(--text-muted)" />
        <span>No personal trade fills recorded yet</span>
      </div>
    );
  }

  return (
    <div style={{ height: '100%', overflowY: 'auto' }}>
      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.72rem' }}>
        <thead>
          <tr
            style={{
              color: 'var(--text-muted)',
              borderBottom: '1px solid var(--border-subtle)',
              textAlign: 'left',
              height: 26,
            }}
          >
            <th style={{ padding: '0 8px' }}>Trade ID</th>
            <th style={{ padding: '0 8px' }}>Time</th>
            <th style={{ padding: '0 8px' }}>Instrument</th>
            <th style={{ padding: '0 8px' }}>Side</th>
            <th style={{ padding: '0 8px', textAlign: 'right' }}>Execution Price</th>
            <th style={{ padding: '0 8px', textAlign: 'right' }}>Quantity</th>
            <th style={{ padding: '0 8px', textAlign: 'right' }}>Total Value</th>
          </tr>
        </thead>
        <tbody>
          {trades.map((t) => {
            const isBuy = t.takerSide === 'BUY';
            const timeStr = t.executedAt
              ? new Date(t.executedAt).toLocaleTimeString()
              : '--:--';
            const val = (parseFloat(t.price) * parseFloat(t.quantity)).toFixed(2);

            return (
              <tr
                key={t.tradeId}
                style={{
                  borderBottom: '1px solid rgba(255,255,255,0.03)',
                  height: 28,
                }}
              >
                <td className="font-mono" style={{ padding: '0 8px', color: 'var(--text-muted)', fontSize: '0.65rem' }}>
                  {t.tradeId.length > 14 ? t.tradeId.substring(0, 14) + '...' : t.tradeId}
                </td>
                <td className="font-mono" style={{ padding: '0 8px', color: 'var(--text-secondary)' }}>
                  {timeStr}
                </td>
                <td style={{ padding: '0 8px', fontWeight: 600, color: 'var(--text-primary)' }}>
                  {t.instrument.replace('_', '/')}
                </td>
                <td
                  style={{
                    padding: '0 8px',
                    fontWeight: 700,
                    color: isBuy ? 'var(--bid-green)' : 'var(--ask-red)',
                  }}
                >
                  {t.takerSide}
                </td>
                <td className="font-mono" style={{ padding: '0 8px', textAlign: 'right', color: 'var(--text-primary)' }}>
                  ${t.price}
                </td>
                <td className="font-mono" style={{ padding: '0 8px', textAlign: 'right', color: 'var(--text-primary)' }}>
                  {t.quantity}
                </td>
                <td className="font-mono" style={{ padding: '0 8px', textAlign: 'right', color: 'var(--text-secondary)' }}>
                  ${val} USDT
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

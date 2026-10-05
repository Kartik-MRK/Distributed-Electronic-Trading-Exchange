'use client';

import React from 'react';
import { BalanceRecord } from '../../types';
import { Wallet, Coins } from 'lucide-react';

interface AccountPanelProps {
  balances: BalanceRecord[];
}

export function AccountPanel({ balances }: AccountPanelProps) {
  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(4, 1fr)',
        gap: 8,
        height: '100%',
        padding: '6px 8px',
        alignItems: 'center',
      }}
    >
      {balances.map((b) => {
        const avail = parseFloat(b.available || '0');
        const reserved = parseFloat(b.reserved || '0');
        const total = parseFloat(b.total || (avail + reserved).toString());
        const reservedPercent = total > 0 ? (reserved / total) * 100 : 0;

        const assetColors: Record<string, string> = {
          USDT: 'var(--bid-green)',
          BTC: '#f7931a',
          ETH: 'var(--accent-cyan)',
          SOL: '#9945ff',
        };

        const accent = assetColors[b.asset] || 'var(--text-primary)';

        return (
          <div
            key={b.asset}
            style={{
              backgroundColor: 'var(--bg-card)',
              border: '1px solid var(--border-subtle)',
              borderRadius: 6,
              padding: '6px 10px',
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'center',
              position: 'relative',
              overflow: 'hidden',
            }}
          >
            {/* Asset Header */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <div
                  style={{
                    width: 18,
                    height: 18,
                    borderRadius: '50%',
                    backgroundColor: 'rgba(255,255,255,0.06)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  <Coins size={12} color={accent} />
                </div>
                <span style={{ fontWeight: 700, fontSize: '0.78rem', color: 'var(--text-primary)' }}>
                  {b.asset}
                </span>
              </div>
              <span className="font-mono" style={{ fontSize: '0.72rem', color: 'var(--text-secondary)' }}>
                Total: {total.toLocaleString()}
              </span>
            </div>

            {/* Available & Reserved Metrics */}
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.7rem' }}>
              <div>
                <span style={{ color: 'var(--text-muted)' }}>Avail: </span>
                <span className="font-mono" style={{ color: 'var(--bid-green)', fontWeight: 600 }}>
                  {avail.toLocaleString()}
                </span>
              </div>
              <div>
                <span style={{ color: 'var(--text-muted)' }}>Reserved: </span>
                <span className="font-mono" style={{ color: 'var(--accent-amber)', fontWeight: 600 }}>
                  {reserved.toLocaleString()}
                </span>
              </div>
            </div>

            {/* Reserved Proportion Bar */}
            <div
              style={{
                height: 3,
                width: '100%',
                backgroundColor: 'rgba(255,255,255,0.08)',
                borderRadius: 2,
                marginTop: 6,
                overflow: 'hidden',
                position: 'relative',
              }}
            >
              <div
                style={{
                  height: '100%',
                  width: `${reservedPercent}%`,
                  backgroundColor: 'var(--accent-amber)',
                  borderRadius: 2,
                }}
              />
            </div>
          </div>
        );
      })}
    </div>
  );
}

'use client';

import React from 'react';
import { OrderRecord } from '../../types';
import { History } from 'lucide-react';

interface OrderHistoryTableProps {
  orders: OrderRecord[];
}

export function OrderHistoryTable({ orders }: OrderHistoryTableProps) {
  if (orders.length === 0) {
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
        <History size={20} color="var(--text-muted)" />
        <span>No past order history found</span>
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
            <th style={{ padding: '0 8px' }}>Time</th>
            <th style={{ padding: '0 8px' }}>Instrument</th>
            <th style={{ padding: '0 8px' }}>Side</th>
            <th style={{ padding: '0 8px' }}>Type</th>
            <th style={{ padding: '0 8px', textAlign: 'right' }}>Price</th>
            <th style={{ padding: '0 8px', textAlign: 'right' }}>Quantity</th>
            <th style={{ padding: '0 8px', textAlign: 'right' }}>Filled</th>
            <th style={{ padding: '0 8px' }}>Status</th>
          </tr>
        </thead>
        <tbody>
          {orders.map((order) => {
            const isBuy = order.side === 'BUY';
            const timeStr = order.createdAt
              ? new Date(order.createdAt).toLocaleTimeString()
              : '--:--';

            const statusColors: Record<string, { bg: string; text: string }> = {
              FILLED: { bg: 'rgba(0, 245, 160, 0.15)', text: 'var(--bid-green)' },
              CANCELLED: { bg: 'rgba(100, 116, 139, 0.2)', text: 'var(--text-secondary)' },
              REJECTED: { bg: 'rgba(255, 59, 105, 0.15)', text: 'var(--ask-red)' },
              ACCEPTED: { bg: 'rgba(0, 210, 255, 0.15)', text: 'var(--accent-cyan)' },
              SUBMITTED: { bg: 'rgba(255, 184, 0, 0.15)', text: 'var(--accent-amber)' },
            };

            const colorScheme = statusColors[order.status] || {
              bg: 'rgba(255,255,255,0.05)',
              text: 'var(--text-muted)',
            };

            return (
              <tr
                key={order.orderId}
                style={{
                  borderBottom: '1px solid rgba(255,255,255,0.03)',
                  height: 28,
                }}
              >
                <td className="font-mono" style={{ padding: '0 8px', color: 'var(--text-secondary)' }}>
                  {timeStr}
                </td>
                <td style={{ padding: '0 8px', fontWeight: 600, color: 'var(--text-primary)' }}>
                  {order.instrument.replace('_', '/')}
                </td>
                <td
                  style={{
                    padding: '0 8px',
                    fontWeight: 700,
                    color: isBuy ? 'var(--bid-green)' : 'var(--ask-red)',
                  }}
                >
                  {order.side}
                </td>
                <td style={{ padding: '0 8px', color: 'var(--text-secondary)' }}>
                  {order.type}
                </td>
                <td className="font-mono" style={{ padding: '0 8px', textAlign: 'right', color: 'var(--text-primary)' }}>
                  {order.price}
                </td>
                <td className="font-mono" style={{ padding: '0 8px', textAlign: 'right', color: 'var(--text-primary)' }}>
                  {order.quantity}
                </td>
                <td className="font-mono" style={{ padding: '0 8px', textAlign: 'right', color: 'var(--text-secondary)' }}>
                  {order.filledQuantity || '0.0000'}
                </td>
                <td style={{ padding: '0 8px' }}>
                  <span
                    style={{
                      backgroundColor: colorScheme.bg,
                      color: colorScheme.text,
                      padding: '2px 6px',
                      borderRadius: 4,
                      fontSize: '0.65rem',
                      fontWeight: 600,
                    }}
                  >
                    {order.status}
                  </span>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

'use client';

import React from 'react';
import { OrderRecord } from '../../types';
import { X, Clock } from 'lucide-react';

interface OpenOrdersTableProps {
  orders: OrderRecord[];
  onCancelOrder: (orderId: string) => Promise<boolean>;
}

export function OpenOrdersTable({ orders, onCancelOrder }: OpenOrdersTableProps) {
  const openOrders = orders.filter(
    (o) => o.status === 'SUBMITTED' || o.status === 'ACCEPTED' || o.status === 'PARTIALLY_FILLED'
  );

  if (openOrders.length === 0) {
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
        <Clock size={20} color="var(--text-muted)" />
        <span>No open orders active for this account</span>
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
            <th style={{ padding: '0 8px', textAlign: 'center' }}>Action</th>
          </tr>
        </thead>
        <tbody>
          {openOrders.map((order) => {
            const isBuy = order.side === 'BUY';
            const timeStr = order.createdAt
              ? new Date(order.createdAt).toLocaleTimeString()
              : '--:--';

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
                      backgroundColor: 'rgba(0, 210, 255, 0.12)',
                      color: 'var(--accent-cyan)',
                      padding: '2px 6px',
                      borderRadius: 4,
                      fontSize: '0.65rem',
                      fontWeight: 600,
                    }}
                  >
                    {order.status}
                  </span>
                </td>
                <td style={{ padding: '0 8px', textAlign: 'center' }}>
                  <button
                    onClick={() => onCancelOrder(order.orderId)}
                    title="Cancel Order"
                    style={{
                      background: 'rgba(255, 59, 105, 0.15)',
                      border: '1px solid rgba(255, 59, 105, 0.3)',
                      color: 'var(--ask-red)',
                      borderRadius: 4,
                      padding: '2px 8px',
                      fontSize: '0.68rem',
                      cursor: 'pointer',
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: 4,
                      fontWeight: 600,
                      transition: 'all 0.15s ease',
                    }}
                    onMouseEnter={(e) => {
                      (e.currentTarget as HTMLElement).style.backgroundColor = 'var(--ask-red)';
                      (e.currentTarget as HTMLElement).style.color = '#ffffff';
                    }}
                    onMouseLeave={(e) => {
                      (e.currentTarget as HTMLElement).style.backgroundColor = 'rgba(255, 59, 105, 0.15)';
                      (e.currentTarget as HTMLElement).style.color = 'var(--ask-red)';
                    }}
                  >
                    <X size={11} /> Cancel
                  </button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

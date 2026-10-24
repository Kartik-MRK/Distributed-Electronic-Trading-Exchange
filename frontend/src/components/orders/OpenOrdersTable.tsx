'use client';

import React, { useState } from 'react';
import { OrderRecord } from '../../types';
import { X, Clock } from 'lucide-react';
import { useToast } from '../common/ToastContext';

interface OpenOrdersTableProps {
  orders: OrderRecord[];
  onCancelOrder: (orderId: string) => Promise<boolean>;
}

export function OpenOrdersTable({
  orders,
  onCancelOrder,
}: OpenOrdersTableProps) {
  const { showToast } = useToast();
  const [pending, setPending] = useState<string | null>(null);
  const requestCancellation = async (orderId: string) => {
    setPending(orderId);
    try {
      await onCancelOrder(orderId);
      showToast('success', 'Cancellation requested', 'Waiting for the exchange to confirm cancellation.');
    } catch (error) {
      showToast('error', 'Cancellation failed', error instanceof Error ? error.message : 'Please try again.');
    } finally {
      setPending(null);
    }
  };
  const openOrders = orders.filter(
    (o) =>
      o.status === 'SUBMITTED' ||
      o.status === 'ACCEPTED' ||
      o.status === 'PARTIALLY_FILLED',
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
    <div style={{ height: '100%', overflow: 'auto' }}>
      <table
        className="data-table"
        style={{
          width: '100%',
          borderCollapse: 'collapse',
          fontSize: '0.72rem',
        }}
      >
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
            <th style={{ padding: '0 8px', textAlign: 'right' }}>Total Qty</th>
            <th style={{ padding: '0 8px', textAlign: 'right' }}>Filled</th>
            <th style={{ padding: '0 8px', textAlign: 'right' }}>Remaining</th>
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

            const totalNum = parseFloat(order.quantity) || 0;
            const filledNum = parseFloat(order.filledQuantity) || 0;
            const remainingNum =
              order.remainingQuantity != null
                ? parseFloat(order.remainingQuantity)
                : Math.max(0, totalNum - filledNum);
            const fillPct =
              totalNum > 0 ? Math.min(100, Math.round((filledNum / totalNum) * 100)) : 0;

            const formattedPrice =
              order.price === 'MARKET'
                ? 'MARKET'
                : `$${Number.isFinite(parseFloat(order.price)) ? parseFloat(order.price).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : order.price}`;

            return (
              <tr
                key={order.orderId}
                style={{
                  borderBottom: '1px solid rgba(255,255,255,0.03)',
                  height: 28,
                }}
              >
                <td
                  className="font-mono"
                  style={{ padding: '0 8px', color: 'var(--text-secondary)' }}
                >
                  {timeStr}
                </td>
                <td
                  style={{
                    padding: '0 8px',
                    fontWeight: 600,
                    color: 'var(--text-primary)',
                  }}
                >
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
                <td
                  style={{ padding: '0 8px', color: 'var(--text-secondary)' }}
                >
                  {order.type}
                </td>
                <td
                  className="font-mono"
                  style={{
                    padding: '0 8px',
                    textAlign: 'right',
                    color: 'var(--text-primary)',
                  }}
                >
                  {formattedPrice}
                </td>
                <td
                  className="font-mono"
                  style={{
                    padding: '0 8px',
                    textAlign: 'right',
                    color: 'var(--text-primary)',
                  }}
                >
                  {order.quantity}
                </td>
                <td
                  className="font-mono"
                  style={{
                    padding: '0 8px',
                    textAlign: 'right',
                    color: filledNum > 0 ? 'var(--bid-green)' : 'var(--text-secondary)',
                  }}
                >
                  {order.filledQuantity || '0.0000'}
                </td>
                <td
                  className="font-mono"
                  style={{
                    padding: '0 8px',
                    textAlign: 'right',
                    color: 'var(--text-secondary)',
                  }}
                >
                  {remainingNum.toFixed(4)}
                </td>
                <td style={{ padding: '0 8px' }}>
                  <span
                    style={{
                      backgroundColor:
                        order.status === 'PARTIALLY_FILLED'
                          ? 'rgba(255, 184, 0, 0.15)'
                          : 'var(--bg-hover)',
                      color:
                        order.status === 'PARTIALLY_FILLED'
                          ? 'var(--accent-amber)'
                          : 'var(--accent-cyan)',
                      padding: '2px 6px',
                      borderRadius: 4,
                      fontSize: '0.65rem',
                      fontWeight: 600,
                    }}
                    title={
                      order.status === 'PARTIALLY_FILLED'
                        ? 'Order is resting in order book with partial fills'
                        : 'Open order resting in order book'
                    }
                  >
                    {order.status === 'PARTIALLY_FILLED'
                      ? `PARTIAL (${fillPct}%)`
                      : 'OPEN'}
                  </span>
                </td>
                <td style={{ padding: '0 8px', textAlign: 'center' }}>
                  <button
                    onClick={() => void requestCancellation(order.orderId)}
                    disabled={pending !== null}
                    title="Cancel Order"
                    style={{
                      background: 'var(--ask-red-bg)',
                      border: '1px solid var(--ask-red-bg)',
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
                      (e.currentTarget as HTMLElement).style.backgroundColor =
                        'var(--ask-red)';
                      (e.currentTarget as HTMLElement).style.color = '#ffffff';
                    }}
                    onMouseLeave={(e) => {
                      (e.currentTarget as HTMLElement).style.backgroundColor =
                        'var(--ask-red-bg)';
                      (e.currentTarget as HTMLElement).style.color =
                        'var(--ask-red)';
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

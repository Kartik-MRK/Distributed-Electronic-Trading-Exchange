'use client';

import React, { useState, useEffect } from 'react';
import { Instrument, OrderSide, OrderType, BalanceRecord } from '../../types';
import { INSTRUMENT_METAS } from '../../lib/api';
import { useToast } from '../common/ToastContext';
import { Wallet, CheckCircle, AlertTriangle } from 'lucide-react';

interface OrderEntryProps {
  instrument: Instrument;
  selectedPriceFromBook?: string;
  balances: BalanceRecord[];
  onPlaceOrder: (params: {
    instrument: Instrument;
    side: OrderSide;
    type: OrderType;
    price: string;
    quantity: string;
  }) => Promise<unknown>;
}

export function OrderEntry({
  instrument,
  selectedPriceFromBook,
  balances,
  onPlaceOrder,
}: OrderEntryProps) {
  const { showToast } = useToast();
  const meta = INSTRUMENT_METAS[instrument];
  const [side, setSide] = useState<OrderSide>('BUY');
  const [type, setType] = useState<OrderType>('LIMIT');
  const [price, setPrice] = useState<string>(meta.lastPrice);
  const [quantity, setQuantity] = useState<string>('0.05');
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);

  // When user clicks a price level in the OrderBook, auto-fill price
  useEffect(() => {
    if (selectedPriceFromBook) {
      setPrice(selectedPriceFromBook);
    }
  }, [selectedPriceFromBook]);

  // When instrument changes, update price default
  useEffect(() => {
    setPrice(meta.lastPrice);
  }, [instrument, meta.lastPrice]);

  // Compute available balance for current side
  const relevantAsset = side === 'BUY' ? meta.quoteAsset : meta.baseAsset;
  const balanceRecord = balances.find((b) => b.asset === relevantAsset);
  const availableBal = parseFloat(balanceRecord?.available || '0');

  // Calculate order total
  const numPrice = type === 'MARKET' ? parseFloat(meta.lastPrice) : parseFloat(price) || 0;
  const numQty = parseFloat(quantity) || 0;
  const estimatedTotal = (numPrice * numQty).toFixed(2);

  // Handle Percentage Quick Fill
  const handlePercentageClick = (percent: number) => {
    if (availableBal <= 0) return;
    if (side === 'BUY') {
      const budget = availableBal * (percent / 100);
      const effPrice = numPrice > 0 ? numPrice : parseFloat(meta.lastPrice);
      const computedQty = (budget / effPrice).toFixed(meta.quantityDecimals);
      setQuantity(computedQty);
    } else {
      const computedQty = (availableBal * (percent / 100)).toFixed(meta.quantityDecimals);
      setQuantity(computedQty);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (numQty <= 0) {
      showToast('error', 'Invalid Quantity', 'Please enter a quantity greater than zero');
      return;
    }
    if (type !== 'MARKET' && numPrice <= 0) {
      showToast('error', 'Invalid Price', 'Please enter a valid price');
      return;
    }

    // Check available balance
    if (side === 'BUY' && parseFloat(estimatedTotal) > availableBal) {
      showToast('warning', 'Insufficient USDT Balance', `Requires $${estimatedTotal}, available $${availableBal}`);
      return;
    }
    if (side === 'SELL' && numQty > availableBal) {
      showToast('warning', `Insufficient ${meta.baseAsset} Balance`, `Requires ${numQty}, available ${availableBal}`);
      return;
    }

    setIsSubmitting(true);
    try {
      await onPlaceOrder({
        instrument,
        side,
        type,
        price: type === 'MARKET' ? meta.lastPrice : price,
        quantity,
      });

      showToast(
        'success',
        `${side} Order Submitted`,
        `${type} ${quantity} ${meta.baseAsset} at ${type === 'MARKET' ? 'MARKET' : '$' + price}`
      );
    } catch (err) {
      showToast(
        'error',
        'Order Placement Failed',
        err instanceof Error ? err.message : 'Order rejected by exchange'
      );
    } finally {
      setIsSubmitting(false);
    }
  };

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
      {/* Side Switcher (Buy / Sell) */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', padding: 6, gap: 6 }}>
        <button
          type="button"
          onClick={() => setSide('BUY')}
          style={{
            padding: '8px 0',
            fontWeight: 700,
            fontSize: '0.82rem',
            borderRadius: 6,
            border: 'none',
            cursor: 'pointer',
            backgroundColor: side === 'BUY' ? 'var(--bid-green)' : 'var(--bg-input)',
            color: side === 'BUY' ? '#07180e' : 'var(--text-secondary)',
            boxShadow: side === 'BUY' ? '0 0 12px var(--bid-green-glow)' : 'none',
            transition: 'all 0.15s ease',
          }}
        >
          BUY {meta.baseAsset}
        </button>

        <button
          type="button"
          onClick={() => setSide('SELL')}
          style={{
            padding: '8px 0',
            fontWeight: 700,
            fontSize: '0.82rem',
            borderRadius: 6,
            border: 'none',
            cursor: 'pointer',
            backgroundColor: side === 'SELL' ? 'var(--ask-red)' : 'var(--bg-input)',
            color: side === 'SELL' ? '#ffffff' : 'var(--text-secondary)',
            boxShadow: side === 'SELL' ? '0 0 12px var(--ask-red-glow)' : 'none',
            transition: 'all 0.15s ease',
          }}
        >
          SELL {meta.baseAsset}
        </button>
      </div>

      {/* Form Body */}
      <form
        onSubmit={handleSubmit}
        style={{
          padding: '12px',
          display: 'flex',
          flexDirection: 'column',
          gap: 12,
          flex: 1,
          justifyContent: 'space-between',
        }}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {/* Order Type Tabs */}
          <div>
            <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)', marginBottom: 4 }}>ORDER TYPE</div>
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(4, 1fr)',
                backgroundColor: 'var(--bg-input)',
                borderRadius: 6,
                padding: 2,
                border: '1px solid var(--border-subtle)',
              }}
            >
              {(['LIMIT', 'MARKET', 'IOC', 'FOK'] as OrderType[]).map((t) => (
                <button
                  key={t}
                  type="button"
                  onClick={() => setType(t)}
                  style={{
                    background: type === t ? 'var(--bg-hover)' : 'transparent',
                    color: type === t ? 'var(--accent-cyan)' : 'var(--text-muted)',
                    border: 'none',
                    borderRadius: 4,
                    padding: '4px 0',
                    fontSize: '0.7rem',
                    fontWeight: 600,
                    cursor: 'pointer',
                  }}
                >
                  {t}
                </button>
              ))}
            </div>
          </div>

          {/* Price Input */}
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.7rem', marginBottom: 4 }}>
              <span style={{ color: 'var(--text-muted)' }}>PRICE</span>
              <span style={{ color: 'var(--text-secondary)' }}>USDT</span>
            </div>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                backgroundColor: 'var(--bg-input)',
                border: '1px solid var(--border-subtle)',
                borderRadius: 6,
                padding: '6px 10px',
              }}
            >
              <input
                type="text"
                disabled={type === 'MARKET'}
                value={type === 'MARKET' ? 'Market Best Price' : price}
                onChange={(e) => setPrice(e.target.value)}
                className="font-mono"
                style={{
                  flex: 1,
                  background: 'none',
                  border: 'none',
                  color: type === 'MARKET' ? 'var(--text-muted)' : 'var(--text-primary)',
                  fontSize: '0.85rem',
                  outline: 'none',
                }}
              />
            </div>
          </div>

          {/* Quantity Input */}
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.7rem', marginBottom: 4 }}>
              <span style={{ color: 'var(--text-muted)' }}>QUANTITY</span>
              <span style={{ color: 'var(--text-secondary)' }}>{meta.baseAsset}</span>
            </div>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                backgroundColor: 'var(--bg-input)',
                border: '1px solid var(--border-subtle)',
                borderRadius: 6,
                padding: '6px 10px',
              }}
            >
              <input
                type="text"
                value={quantity}
                onChange={(e) => setQuantity(e.target.value)}
                className="font-mono"
                style={{
                  flex: 1,
                  background: 'none',
                  border: 'none',
                  color: 'var(--text-primary)',
                  fontSize: '0.85rem',
                  outline: 'none',
                }}
              />
            </div>
          </div>

          {/* Quick Percentage Chips */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 6 }}>
            {[25, 50, 75, 100].map((pct) => (
              <button
                key={pct}
                type="button"
                onClick={() => handlePercentageClick(pct)}
                style={{
                  backgroundColor: 'var(--bg-input)',
                  border: '1px solid var(--border-subtle)',
                  borderRadius: 4,
                  padding: '4px 0',
                  color: 'var(--text-secondary)',
                  fontSize: '0.68rem',
                  cursor: 'pointer',
                  transition: 'all 0.15s ease',
                }}
                onMouseEnter={(e) => {
                  (e.currentTarget as HTMLElement).style.borderColor = 'var(--accent-cyan)';
                  (e.currentTarget as HTMLElement).style.color = 'var(--accent-cyan)';
                }}
                onMouseLeave={(e) => {
                  (e.currentTarget as HTMLElement).style.borderColor = 'var(--border-subtle)';
                  (e.currentTarget as HTMLElement).style.color = 'var(--text-secondary)';
                }}
              >
                {pct}%
              </button>
            ))}
          </div>

          {/* Order Summary & Estimated Total */}
          <div
            style={{
              backgroundColor: 'var(--bg-input)',
              borderRadius: 6,
              padding: '8px 10px',
              border: '1px solid var(--border-subtle)',
              fontSize: '0.72rem',
              display: 'flex',
              flexDirection: 'column',
              gap: 4,
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span style={{ color: 'var(--text-muted)' }}>Estimated Value</span>
              <span className="font-mono" style={{ color: 'var(--text-primary)', fontWeight: 600 }}>
                ${estimatedTotal} USDT
              </span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ color: 'var(--text-muted)', display: 'flex', alignItems: 'center', gap: 4 }}>
                <Wallet size={12} color="var(--accent-cyan)" /> Available
              </span>
              <span className="font-mono" style={{ color: 'var(--text-secondary)' }}>
                {availableBal.toLocaleString()} {relevantAsset}
              </span>
            </div>
          </div>
        </div>

        {/* Submit Button */}
        <button
          type="submit"
          disabled={isSubmitting}
          className={side === 'BUY' ? 'btn-buy' : 'btn-sell'}
          style={{
            width: '100%',
            padding: '11px 0',
            borderRadius: 6,
            fontSize: '0.88rem',
            letterSpacing: '0.02em',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 6,
          }}
        >
          {isSubmitting ? (
            <span>TRANSMITTING...</span>
          ) : (
            <span>
              {side} {meta.baseAsset}
            </span>
          )}
        </button>
      </form>
    </div>
  );
}

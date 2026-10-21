'use client';

import React, { useState, useEffect } from 'react';
import { Instrument, OrderSide, OrderType, BalanceRecord } from '../../types';
import { INSTRUMENT_METAS } from '../../lib/api';
import { useToast } from '../common/ToastContext';

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

  // Compute available balance for current side (support USD or USDT quote assets)
  const relevantAsset = side === 'BUY' ? meta.quoteAsset : meta.baseAsset;
  const balanceRecord = balances.find(
    (b) =>
      b.asset === relevantAsset ||
      (relevantAsset === 'USDT' && b.asset === 'USD') ||
      (relevantAsset === 'USD' && b.asset === 'USDT')
  );
  const availableBal = parseFloat(balanceRecord?.available || '0');

  // Calculate order total
  const numPrice =
    type === 'MARKET' ? parseFloat(meta.lastPrice) : parseFloat(price) || 0;
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
      const computedQty = (availableBal * (percent / 100)).toFixed(
        meta.quantityDecimals,
      );
      setQuantity(computedQty);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!Number.isFinite(numQty) || numQty <= 0) {
      showToast(
        'error',
        'Invalid Quantity',
        'Please enter a quantity greater than zero',
      );
      return;
    }
    if (!Number.isFinite(numPrice) || numPrice <= 0) {
      showToast('error', 'Invalid Price', 'Please enter a valid price');
      return;
    }

    // Check available balance
    if (side === 'BUY' && parseFloat(estimatedTotal) > availableBal) {
      showToast(
        'warning',
        `Insufficient ${relevantAsset} Balance`,
        `Requires $${estimatedTotal}, available $${availableBal.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`,
      );
      return;
    }
    if (side === 'SELL' && numQty > availableBal) {
      showToast(
        'warning',
        `Insufficient ${meta.baseAsset} Balance`,
        `Requires ${numQty}, available ${availableBal.toLocaleString('en-US', { maximumFractionDigits: 4 })}`,
      );
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
        `${type} ${quantity} ${meta.baseAsset} at ${type === 'MARKET' ? 'MARKET' : '$' + price}`,
      );
    } catch (err) {
      showToast(
        'error',
        'Order Placement Failed',
        err instanceof Error ? err.message : 'Order rejected by exchange',
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="panel order-entry">
      <div className="panel-heading">
        <h2>Place order</h2>
        <span className="badge">Spot</span>
      </div>
      <form onSubmit={handleSubmit} className="order-form">
        <div className="segmented side-selector" aria-label="Order side">
          {(['BUY', 'SELL'] as OrderSide[]).map((value) => (
            <button
              key={value}
              type="button"
              aria-pressed={side === value}
              onClick={() => setSide(value)}
            >
              {value === 'BUY' ? 'Buy' : 'Sell'} {meta.baseAsset}
            </button>
          ))}
        </div>
        <div className="field">
          <span className="field-label">Order type</span>
          <div className="segmented" aria-label="Order type">
            {(['LIMIT', 'MARKET', 'IOC', 'FOK'] as OrderType[]).map((value) => (
              <button
                key={value}
                type="button"
                aria-pressed={type === value}
                onClick={() => setType(value)}
              >
                {value === 'LIMIT'
                  ? 'Limit'
                  : value === 'MARKET'
                    ? 'Market'
                    : value}
              </button>
            ))}
          </div>
        </div>
        <p className="order-hint">
          {type === 'MARKET'
            ? 'Execute at the best available market price.'
            : type === 'IOC'
              ? 'Fill immediately; cancel any unfilled quantity.'
              : type === 'FOK'
                ? 'Fill the entire order immediately or cancel it.'
                : 'Set the price at which you want to trade.'}
        </p>
        <div className="field">
          <label htmlFor="order-price">
            Price <span>{meta.quoteAsset}</span>
          </label>
          <input
            id="order-price"
            className="font-mono"
            inputMode="decimal"
            type={type === 'MARKET' ? 'text' : 'number'}
            min="0"
            step="any"
            required
            disabled={type === 'MARKET'}
            value={type === 'MARKET' ? 'Best available price' : price}
            onChange={(e) => setPrice(e.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="order-quantity">
            Quantity <span>{meta.baseAsset}</span>
          </label>
          <input
            id="order-quantity"
            className="font-mono"
            inputMode="decimal"
            type="number"
            min="0"
            step="any"
            required
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
          />
        </div>
        <div className="percentage-buttons" aria-label="Use available balance">
          {[25, 50, 75, 100].map((pct) => (
            <button
              type="button"
              key={pct}
              onClick={() => handlePercentageClick(pct)}
            >
              {pct}%
            </button>
          ))}
        </div>
        <dl className="order-summary">
          <div>
            <dt>Available</dt>
            <dd>
              {availableBal.toLocaleString('en-US', {
                minimumFractionDigits: 2,
                maximumFractionDigits: 4,
              })}{' '}
              <span>{relevantAsset}</span>
            </dd>
          </div>
          <div>
            <dt>Estimated total</dt>
            <dd>
              {Number(estimatedTotal).toLocaleString('en-US', {
                minimumFractionDigits: 2,
              })}{' '}
              <span>{meta.quoteAsset}</span>
            </dd>
          </div>
        </dl>
        <button
          type="submit"
          className="button primary order-submit"
          disabled={isSubmitting}
        >
          {isSubmitting
            ? 'Submitting order...'
            : `${side === 'BUY' ? 'Buy' : 'Sell'} ${meta.baseAsset}`}
        </button>
      </form>
    </div>
  );
}

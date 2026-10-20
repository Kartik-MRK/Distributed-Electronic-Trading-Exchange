'use client';
import { Instrument, PriceLevel } from '@/types';
import { useOrderBook } from '@/hooks/useOrderBook';
export function OrderBook({
  instrument,
  onSelectPrice,
}: {
  instrument: Instrument;
  onSelectPrice: (price: string) => void;
}) {
  const { orderBook } = useOrderBook(instrument);
  const row = (level: PriceLevel, bid: boolean) => (
    <button
      className="depth-row font-mono"
      key={level.price}
      onClick={() => onSelectPrice(level.price)}
      aria-label={`Set order price to ${level.price} USDT`}
    >
      <span
        className={`depth-fill ${bid ? 'bid' : 'ask'}`}
        style={{
          width: `${Math.min(100, Math.max(0, level.depthPercent || 0))}%`,
        }}
      />
      <span className={bid ? 'positive' : 'negative'}>{level.price}</span>
      <span>{level.quantity}</span>
      <span className="muted">{level.total}</span>
    </button>
  );
  return (
    <div className="order-book">
      <div className="depth-columns">
        <span>Price (USDT)</span>
        <span>Size ({instrument.split('_')[0]})</span>
        <span>Total</span>
      </div>
      <div className="depth-ladder asks" aria-label="Ask prices">
        {orderBook.asks.map((level) => row(level, false))}
      </div>
      <div className="spread-row">
        <strong className="font-mono">
          {orderBook.bids[0]?.price || '--'}
        </strong>
        <span>
          Spread <span className="font-mono">{orderBook.spread}</span>
        </span>
      </div>
      <div className="depth-ladder" aria-label="Bid prices">
        {orderBook.bids.map((level) => row(level, true))}
      </div>
      <p className="panel-footnote">Select a price to fill your order.</p>
    </div>
  );
}

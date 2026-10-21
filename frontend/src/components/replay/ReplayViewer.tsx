'use client';
import { useEffect, useState } from 'react';
import { Instrument, ReplayTradeRecord } from '@/types';
import { api } from '@/lib/api';
import { Loader } from '@/components/common/Loader';

export function ReplayViewer({
  instrument: initialInstrument,
  onClose,
}: {
  instrument: Instrument;
  onClose?: () => void;
}) {
  const [instrument, setInstrument] = useState(initialInstrument);
  const [isPlaying, setIsPlaying] = useState(false);
  const [speed, setSpeed] = useState(1);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [trades, setTrades] = useState<ReplayTradeRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [preview, setPreview] = useState(false);
  const [timeWindowMinutes, setTimeWindowMinutes] = useState(15);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setIsPlaying(false);
    setCurrentIndex(0);
    setTrades([]);
    const now = Date.now();
    api.marketData
      .getReplay(
        instrument.replace('_', '-'),
        now - timeWindowMinutes * 60000,
        now,
        200,
      )
      .then((data) => {
        if (active) {
          setTrades(data);
          setPreview(false);
        }
      })
      .catch(() => {
        if (active) {
          setTrades(generateSyntheticTrades(instrument, 60));
          setPreview(true);
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [instrument, timeWindowMinutes]);
  useEffect(() => {
    if (!isPlaying || trades.length === 0) return;
    if (currentIndex >= trades.length - 1) {
      setIsPlaying(false);
      return;
    }
    const timer = setTimeout(
      () => setCurrentIndex((index) => index + 1),
      Math.max(20, 100 / speed),
    );
    return () => clearTimeout(timer);
  }, [isPlaying, currentIndex, speed, trades.length]);
  const frame = trades[currentIndex];
  const mid = frame ? frame.price / 100000000 : 0;
  return (
    <div className="replay-viewer">
      <div className="replay-header">
        <div>
          <h2>Historical replay</h2>
          <p>
            {loading
              ? 'Loading events...'
              : preview
                ? 'Sample events / Illustrative depth around each execution.'
                : 'Recorded executions / Illustrative depth around each execution.'}
          </p>
        </div>
        <div className="toolbar">
          <select
            aria-label="Replay instrument"
            value={instrument}
            onChange={(e) => setInstrument(e.target.value as Instrument)}
          >
            {['BTC_USDT', 'ETH_USDT', 'SOL_USDT'].map((pair) => (
              <option key={pair} value={pair}>
                {pair.replace('_', '/')}
              </option>
            ))}
          </select>
          <select
            aria-label="Replay time window"
            value={timeWindowMinutes}
            onChange={(e) => setTimeWindowMinutes(Number(e.target.value))}
          >
            {[5, 15, 30, 60].map((minutes) => (
              <option key={minutes} value={minutes}>
                Past {minutes} minutes
              </option>
            ))}
          </select>
          {onClose && (
            <button className="button" onClick={onClose}>
              Close
            </button>
          )}
        </div>
      </div>
      <div className="replay-controls">
        <div className="toolbar">
          <button
            className="button primary"
            disabled={loading || trades.length === 0}
            onClick={() => {
              if (currentIndex >= trades.length - 1) setCurrentIndex(0);
              setIsPlaying(!isPlaying);
            }}
          >
            {isPlaying ? 'Pause' : 'Play replay'}
          </button>
          <button
            className="button"
            disabled={loading || trades.length === 0}
            onClick={() => {
              setCurrentIndex(0);
              setIsPlaying(false);
            }}
          >
            Restart
          </button>
          <div className="segmented" aria-label="Replay speed">
            {[1, 2, 5, 10].map((value) => (
              <button
                key={value}
                aria-pressed={speed === value}
                onClick={() => setSpeed(value)}
              >
                {value}x
              </button>
            ))}
          </div>
          <span className="muted font-mono">
            Frame {trades.length ? currentIndex + 1 : 0} / {trades.length}
          </span>
        </div>
        <input
          aria-label="Replay position"
          type="range"
          min={0}
          max={Math.max(0, trades.length - 1)}
          value={currentIndex}
          disabled={loading || trades.length === 0}
          onChange={(event) => {
            setIsPlaying(false);
            setCurrentIndex(Number(event.target.value));
          }}
        />
      </div>
      {frame ? (
        <div className="replay-grid">
          <section className="panel content-pad">
            <div className="detail-row">
              <h2>Illustrative depth</h2>
              <strong className="font-mono">{mid.toFixed(2)}</strong>
            </div>
            <div className="replay-depth">
              {[true, false].map((bid) => (
                <div key={String(bid)}>
                  <span className={bid ? 'positive' : 'negative'}>
                    {bid ? 'Bids' : 'Asks'}
                  </span>
                  {Array.from({ length: 6 }, (_, index) => (
                    <div className="detail-row font-mono" key={index}>
                      <span className={bid ? 'positive' : 'negative'}>
                        {(
                          mid *
                          (1 + (bid ? -1 : 1) * (index + 1) * 0.001)
                        ).toFixed(2)}
                      </span>
                      <span className="muted">
                        {(0.01 + ((index * 17) % 50) * 0.005).toFixed(4)}
                      </span>
                    </div>
                  ))}
                </div>
              ))}
            </div>
          </section>
          <section className="panel content-pad">
            <h2>Execution details</h2>
            <dl className="detail-list" style={{ marginTop: 14 }}>
              {[
                ['Event ID', frame.tradeId],
                ['Sequence', frame.sequenceNumber],
                ['Price', (frame.price / 100000000).toFixed(2)],
                ['Quantity', frame.quantity / 100000000],
                ['Buyer', frame.buyAccountId],
                ['Seller', frame.sellAccountId],
                ['Executed at', new Date(frame.executedAt).toLocaleString()],
              ].map(([label, value]) => (
                <div className="detail-row" key={label}>
                  <dt className="muted">{label}</dt>
                  <dd className="font-mono">{value}</dd>
                </div>
              ))}
            </dl>
          </section>
        </div>
      ) : loading ? (
        <Loader label="Loading replay..." />
      ) : (
        <p className="empty-state">No executions found in this time window.</p>
      )}
    </div>
  );
}

function generateSyntheticTrades(
  instrument: Instrument,
  count: number,
): ReplayTradeRecord[] {
  const basePrice =
    instrument === 'BTC_USDT' ? 65000 : instrument === 'ETH_USDT' ? 3500 : 150;
  const list: ReplayTradeRecord[] = [];
  const now = Date.now();

  for (let i = 0; i < count; i++) {
    const delta =
      (Math.sin(i / 5) * 0.01 + (i % 3 === 0 ? 0.002 : -0.002)) * basePrice;
    const price = Math.round((basePrice + delta) * 100000000);
    const quantity = Math.round((0.01 + (i % 5) * 0.02) * 100000000);
    const time = new Date(now - (count - i) * 2000).toISOString();

    list.push({
      tradeId: `trade-replay-${i + 1}`,
      instrument,
      sequenceNumber: 1000 + i,
      price,
      quantity,
      buyOrderId: `order-b-${i}`,
      sellOrderId: `order-s-${i}`,
      buyAccountId: '00000000-0000-0000-0000-000000000001',
      sellAccountId: '00000000-0000-0000-0000-000000000002',
      executedAt: time,
    });
  }

  return list;
}

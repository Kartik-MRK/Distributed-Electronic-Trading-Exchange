'use client';

import { Instrument } from '@/types';
import { INSTRUMENT_METAS } from '@/lib/api';
import { ConnectionState } from '@/lib/ws';
import { AppHeader } from '@/components/common/AppHeader';
import { MarketSelector } from './MarketSelector';

interface HeaderProps {
  selectedInstrument: Instrument;
  onSelectInstrument: (instrument: Instrument) => void;
  connectionState: ConnectionState;
  latency: number;
}

export function Header({
  selectedInstrument,
  onSelectInstrument,
  connectionState,
  latency,
}: HeaderProps) {
  const meta = INSTRUMENT_METAS[selectedInstrument];
  const connected = connectionState === 'connected';
  return (
    <>
      <AppHeader />
      <section className="market-bar" aria-label="Market overview">
        <div className="market-selector">
          <span className="asset-mark">{meta.baseAsset.slice(0, 1)}</span>
          <MarketSelector
            value={selectedInstrument}
            onChange={onSelectInstrument}
          />
          <span className="badge compact-feed-status">
            {connected ? 'Connected' : 'Offline'}
          </span>
        </div>
        <dl className="market-stats">
          <div>
            <dt>Default order price</dt>
            <dd className="market-price">
              {Number(meta.lastPrice).toLocaleString('en-US', {
                minimumFractionDigits: meta.priceDecimals,
              })}
              <small> USD</small>
            </dd>
          </div>
          <div>
            <dt>24h statistics</dt>
            <dd className="muted">Unavailable</dd>
          </div>
        </dl>
        <div className="connection-status" role="status">
          <span className={`status-dot ${connected ? 'connected' : ''}`} />
          <div>
            {connected ? 'Feed connected' : 'Feed unavailable'}
            <small>
              {connected ? (latency > 0 ? `${latency} ms latency` : 'Live connection') : 'Feed unavailable'}
            </small>
          </div>
        </div>
      </section>
    </>
  );
}

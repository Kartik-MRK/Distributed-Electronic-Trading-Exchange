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
            {connected ? 'Connected' : 'Preview'}
          </span>
        </div>
        <dl className="market-stats">
          <div>
            <dt>Reference price</dt>
            <dd className="market-price">
              {Number(meta.lastPrice).toLocaleString('en-US', {
                minimumFractionDigits: meta.priceDecimals,
              })}
              <small> USDT</small>
            </dd>
          </div>
          <div>
            <dt>24h change</dt>
            <dd
              className={
                meta.change24h.startsWith('+') ? 'positive' : 'negative'
              }
            >
              {meta.change24h}
            </dd>
          </div>
          <div>
            <dt>24h high</dt>
            <dd>
              {Number(meta.high24h.replaceAll(',', '')).toLocaleString('en-US')}
            </dd>
          </div>
          <div>
            <dt>24h low</dt>
            <dd>
              {Number(meta.low24h.replaceAll(',', '')).toLocaleString('en-US')}
            </dd>
          </div>
          <div>
            <dt>24h volume</dt>
            <dd>{meta.volume24h}</dd>
          </div>
        </dl>
        <div className="connection-status" role="status">
          <span className={`status-dot ${connected ? 'connected' : ''}`} />
          <div>
            {connected ? 'Feed connected' : 'Preview mode'}
            <small>
              {connected ? `${latency} ms latency` : 'Sample market data'}
            </small>
          </div>
        </div>
      </section>
    </>
  );
}

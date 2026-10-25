'use client';

import { useCallback, useEffect, useState } from 'react';
import { MarketSelector } from '@/components/header/MarketSelector';
import { AppHeader } from '@/components/common/AppHeader';
import { api } from '@/lib/api';
import { Instrument, SystemMetricsSnapshot } from '@/types';
import { TradeTape } from '@/components/tradetape/TradeTape';
import { ExternalLink, RefreshCw } from 'lucide-react';

export default function SystemDashboardPage() {
  const [metrics, setMetrics] = useState<SystemMetricsSnapshot | null>(null);
  const [refreshInterval, setRefreshInterval] = useState(2000);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const [unavailable, setUnavailable] = useState(false);
  const [instrument, setInstrument] = useState<Instrument>('BTC_USD');
  const fetchData = useCallback(async () => {
    try {
      setMetrics(await api.metrics.getSnapshot());
      setUnavailable(false);
    } catch {
      setUnavailable(true);
    }
    setLastUpdated(new Date());
  }, []);
  useEffect(() => {
    void fetchData();
    const timer = setInterval(fetchData, refreshInterval);
    return () => clearInterval(timer);
  }, [fetchData, refreshInterval]);
  const healthy =
    metrics?.servicesHealth.filter((service) => service.status === 'UP')
      .length ?? 0;
  return (
    <>
      <AppHeader />
      <main className="page-content">
        <div className="page-heading">
          <div>
            <span className="eyebrow">Exchange operations</span>
            <h1>System telemetry</h1>
            <p>Service health, processing performance, and market activity.</p>
          </div>
          <div className="toolbar">
            <span className="muted">Refresh</span>
            <div className="segmented" aria-label="Refresh interval">
              {[1000, 2000, 5000].map((ms) => (
                <button
                  key={ms}
                  aria-pressed={refreshInterval === ms}
                  onClick={() => setRefreshInterval(ms)}
                >
                  {ms / 1000}s
                </button>
              ))}
            </div>
            <button
              className="icon-button"
              aria-label="Refresh metrics"
              onClick={fetchData}
            >
              <RefreshCw size={16} />
            </button>
          </div>
        </div>
        {unavailable && (
          <div className="notice" role="status">
            <span>
              The metrics service is unavailable.{' '}
              {metrics
                ? 'Showing the last received snapshot.'
                : 'Connect the exchange services to view telemetry.'}
            </span>
            <span className="eyebrow">
              {lastUpdated
                ? `Checked ${lastUpdated.toLocaleTimeString()}`
                : 'Connecting'}
            </span>
          </div>
        )}
        <section className="stats-grid" aria-label="System overview">
          <div className="panel stat-card">
            <span>Matching throughput</span>
            <strong>{metrics?.matchingThroughput?.toFixed(1) ?? '—'}</strong>
            <small>trades per second</small>
          </div>
          <div className="panel stat-card">
            <span>Median latency</span>
            <strong>
              {metrics?.e2eLatency?.p50Ms ?? '—'} <small>ms</small>
            </strong>
            <small>
              P95 {metrics?.e2eLatency?.p95Ms ?? '—'} ms · P99{' '}
              {metrics?.e2eLatency?.p99Ms ?? '—'} ms
            </small>
          </div>
          <div className="panel stat-card">
            <span>WebSocket connections</span>
            <strong>{metrics?.activeWebsocketConnections ?? '—'}</strong>
            <small>active client sessions</small>
          </div>
          <div className="panel stat-card">
            <span>Service health</span>
            <strong>
              {metrics ? `${healthy} / ${metrics.servicesHealth.length}` : '—'}
            </strong>
            <small>services reporting online</small>
          </div>
        </section>
        <div className="section-grid">
          <section className="panel">
            <div className="panel-heading">
              <h2>Service health</h2>
              <span className="eyebrow">Cluster</span>
            </div>
            {metrics ? (
              <div className="service-grid content-pad">
                {metrics.servicesHealth.map((service) => (
                  <div className="service-card" key={service.service}>
                    <strong>{service.service}</strong>
                    <p>
                      <span>Port {service.port}</span>
                      <span
                        className={
                          service.status === 'UP' ? 'positive' : 'negative'
                        }
                      >
                        {service.status}
                      </span>
                    </p>
                  </div>
                ))}
              </div>
            ) : (
              <p className="empty-state">Waiting for service health data.</p>
            )}
          </section>
          <section className="panel">
            <div className="panel-heading">
              <h2>Consumer lag</h2>
              <span className="eyebrow">Kafka</span>
            </div>
            {metrics?.kafkaConsumerLag.length ? (
              <div className="detail-list content-pad">
                {metrics.kafkaConsumerLag.map((item) => (
                  <div className="detail-row" key={item.topic}>
                    <div>
                      {item.topic}
                      <small>{item.group}</small>
                    </div>
                    <span className="font-mono">{item.lag} msg</span>
                  </div>
                ))}
              </div>
            ) : (
              <p className="empty-state">No consumer metrics available.</p>
            )}
          </section>
        </div>
        <div className="section-grid">
          <section className="panel">
            <div className="panel-heading">
              <h2>Memory utilization</h2>
              <span className="eyebrow">JVM heap</span>
            </div>
            {metrics?.jvmMemory.length ? (
              <div className="detail-list content-pad">
                {metrics.jvmMemory.map((memory) => (
                  <div key={memory.service}>
                    <div className="detail-row">
                      <span>{memory.service}</span>
                      <span className="font-mono muted">
                        {memory.usedMb} / {memory.maxMb} MB
                      </span>
                    </div>
                    <div className="progress-track">
                      <span
                        style={{
                          width: `${Math.min(100, memory.maxMb > 0 ? (memory.usedMb / memory.maxMb) * 100 : 0)}%`,
                          backgroundColor:
                            memory.maxMb <= 0
                              ? 'var(--text-muted)'
                              : memory.usedMb / memory.maxMb > 0.8
                                ? 'var(--ask-red)'
                                : memory.usedMb / memory.maxMb > 0.6
                                  ? 'var(--accent-amber)'
                                  : 'var(--bid-green)',
                        }}
                      />
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <p className="empty-state">No memory metrics available.</p>
            )}
          </section>
          <section className="panel">
            <div className="panel-heading">
              <h2>Recent market trades</h2>
              <MarketSelector
                label="Trade feed instrument"
                showLabel={false}
                value={instrument}
                onChange={setInstrument}
              />
            </div>
            <div style={{ height: 210 }}>
              {lastUpdated && (
                <TradeTape key={instrument} instrument={instrument} />
              )}
            </div>
          </section>
        </div>
        <div className="toolbar" style={{ marginTop: 24 }}>
          <a
            className="button"
            href="http://localhost:3000"
            target="_blank"
            rel="noreferrer"
          >
            Grafana <ExternalLink size={13} />
          </a>
          <a
            className="button"
            href="http://localhost:16686"
            target="_blank"
            rel="noreferrer"
          >
            Jaeger traces <ExternalLink size={13} />
          </a>
          <span className="muted">
            {!unavailable && lastUpdated
              ? `Last updated ${lastUpdated.toLocaleTimeString()}`
              : 'Local observability tools'}
          </span>
        </div>
      </main>
    </>
  );
}

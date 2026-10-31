 'use client';

import { useState } from 'react';
import { api } from '@/lib/api';
import { useSimulationClock } from '@/hooks/useSimulationClock';
import { Loader } from '@/components/common/Loader';

type Duration = 60 | 120 | 300 | 600;
const durationLabel = (ms: number) => {
  const seconds = Math.floor(ms / 1000);
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
};

export function MarketSimulationControl() {
  const clock = useSimulationClock();
  const [duration, setDuration] = useState<Duration>(60);
  const [starting, setStarting] = useState(false);
  const [error, setError] = useState('');
  const snapshot = clock.snapshot;
  const finalizing = snapshot?.status === 'STOPPING' || (snapshot?.status === 'ACTIVE' && clock.remainingMs === 0);
  const canStart = clock.fresh && snapshot && ['IDLE', 'COMPLETED', 'FAILED', 'STOPPING'].includes(snapshot.status);
  const label = starting || snapshot?.status === 'STARTING' ? 'Preparing market'
    : !clock.fresh ? 'Status unavailable'
    : clock.tradingAllowed ? 'Running'
    : finalizing ? 'Finalizing'
    : snapshot?.status === 'MAINTENANCE' || snapshot?.status === 'UNINITIALIZED' ? 'Maintenance' : 'Paused';
  const start = async () => {
    setStarting(true); setError('');
    try { await api.simulator.start(duration); }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Unable to start the simulation.'); }
    finally { await clock.refresh(); setStarting(false); }
  };
  return (
    <section className="panel simulation-control" aria-label="Market simulation controls">
      <div className="panel-heading">
        <div><span className="eyebrow">Shared across all traders</span><h2>Simulate market</h2></div>
        <span className="badge" role="status">{label}</span>
      </div>
      <div className="content-pad simulation-control-body">
        <label><span className="muted">Duration</span>
          <select aria-label="Simulation duration" value={duration} disabled={starting || !canStart}
            onChange={event => setDuration(Number(event.target.value) as Duration)}>
            <option value={60}>1 minute</option><option value={120}>2 minutes</option>
            <option value={300}>5 minutes</option><option value={600}>10 minutes</option>
          </select>
        </label>
        <button className="button" disabled={starting || !canStart} aria-busy={starting} onClick={start}>
          {starting ? <Loader variant="inline" label="Preparing..." /> : snapshot?.status === 'STOPPING' ? 'Retry cleanup and start' : 'Start simulation'}
        </button>
        <div className="simulation-session-info">
          <strong>Market elapsed: {durationLabel(clock.elapsedMs)}</strong>
          <span>{clock.tradingAllowed ? `${Math.ceil(clock.remainingMs / 1000)}s remaining` : finalizing ? 'Settling fills and releasing unused funds' : 'Market time is paused'}</span>
          {snapshot?.initializedAt && <small>Simulation starts at {new Date(snapshot.initializedAt).toLocaleString()}</small>}
          <small>BTC, ETH and SOL share this clock. Anyone can trade during an active session.</small>
        </div>
        {error && <div className="notice error" role="alert">{error}</div>}
      </div>
    </section>
  );
}

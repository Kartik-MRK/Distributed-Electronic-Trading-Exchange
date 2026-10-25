'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, ApiError } from '@/lib/api';
import { getCurrentUser } from '@/lib/auth';
import { SimulatorStatus } from '@/types';
import { Loader } from '@/components/common/Loader';

type Duration = 30 | 60 | 120;

export function MarketSimulationControl() {
  const [session, setSession] = useState<SimulatorStatus | null>(null);
  const [duration, setDuration] = useState<Duration>(60);
  const [starting, setStarting] = useState(false);
  const [now, setNow] = useState(0);
  const [userId, setUserId] = useState<string | undefined>();
  const [error, setError] = useState('');

  const refresh = useCallback(async () => {
    try {
      setSession(await api.simulator.getStatus());
    } catch {
      setSession(null);
    }
  }, []);

  useEffect(() => {
    void refresh();
    setUserId(getCurrentUser()?.userId);
    const poll = setInterval(() => void refresh(), 2000);
    const clock = setInterval(() => setNow(Date.now()), 1000);
    setNow(Date.now());
    return () => {
      clearInterval(poll);
      clearInterval(clock);
    };
  }, [refresh]);

  const active = session?.status === 'ACTIVE' || session?.status === 'STOPPING';
  const remaining = session?.expiresAt
    ? Math.max(0, Math.ceil((new Date(session.expiresAt).getTime() - now) / 1000))
    : 0;
  const label = starting
    ? 'Starting'
    : session?.status === 'ACTIVE'
      ? 'Running'
      : session?.status === 'STOPPING'
        ? 'Stopping'
        : session?.status === 'COMPLETED'
          ? 'Ended'
          : session?.status === 'FAILED'
            ? 'Failed'
            : session?.status === 'IDLE'
              ? 'Idle'
              : 'Loading';

  const start = async () => {
    setStarting(true);
    setError('');
    try {
      setSession(await api.simulator.start(duration));
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 409) {
        const current = await api.simulator.getStatus().catch(() => null);
        if (current) setSession(current);
        setError(current?.ownerUserId === userId
          ? 'Your market simulation session is already active.'
          : 'Another user has already started the global market simulation.');
      } else {
        setError(cause instanceof Error ? cause.message : 'Unable to start the simulation.');
      }
    } finally {
      setStarting(false);
    }
  };

  return (
    <section className="panel simulation-control" aria-label="Market simulation controls">
      <div className="panel-heading">
        <div>
          <span className="eyebrow">Shared across all traders</span>
          <h2>Simulate market</h2>
        </div>
        <span className="badge" role="status">{label}</span>
      </div>
      <div className="content-pad simulation-control-body">
        <label>
          <span className="muted">Duration</span>
          <select
            aria-label="Simulation duration"
            value={duration}
            disabled={starting || Boolean(active) || !session}
            onChange={(event) => setDuration(Number(event.target.value) as Duration)}
          >
            <option value={30}>30 seconds</option>
            <option value={60}>1 minute</option>
            <option value={120}>2 minutes</option>
          </select>
        </label>
        <button className="button" disabled={starting || Boolean(active) || !session} aria-busy={starting} onClick={start}>
          {starting ? <Loader variant="inline" label="Starting..." /> : 'Start simulation'}
        </button>
        {active && (
          <div className="simulation-session-info" role="status">
            <strong>{session?.ownerUserId === userId ? 'Your session' : 'Another trader started the session'}</strong>
            <span>{remaining > 0 ? `${remaining}s remaining` : 'Finishing'}</span>
            {session?.startsAt && session.expiresAt && (
              <small>
                Started {new Date(session.startsAt).toLocaleTimeString()}
                {' · '}Ends {new Date(session.expiresAt).toLocaleTimeString()}
              </small>
            )}
          </div>
        )}
        {session?.status === 'COMPLETED' && <span className="muted">The previous session has ended.</span>}
        {session?.status === 'FAILED' && <span className="muted">The previous session ended with an error.</span>}
        {error && <div className="notice error" role="alert">{error}</div>}
      </div>
    </section>
  );
}

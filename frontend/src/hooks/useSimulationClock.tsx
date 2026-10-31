 'use client';

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { api } from '@/lib/api';
import { newerSnapshot, projectClock } from '@/lib/simulation-clock';
import type { MarketClockSnapshot } from '@/types';

type ClockState = ReturnType<typeof projectClock> & { snapshot: MarketClockSnapshot | null; refresh: () => Promise<void> };
const SimulationContext = createContext<ClockState | null>(null);

export function SimulationClockProvider({ children }: { children: ReactNode }) {
  const [sample, setSample] = useState<{ snapshot: MarketClockSnapshot | null; receivedAt: number; connected: boolean }>({snapshot: null, receivedAt: 0, connected: false});
  const [now, setNow] = useState(0);
  const busy = useRef(false);
  const mounted = useRef(false);
  const refresh = useCallback(async () => {
    if (busy.current) return;
    busy.current = true;
    const requestedAt = performance.now();
    try {
      const status = await api.simulator.getStatus();
      if (!status.clock) throw new Error('Clock unavailable');
      if (mounted.current) setSample(previous => newerSnapshot(previous.snapshot, status.clock)
        ? {snapshot: status.clock, receivedAt: requestedAt, connected: true} : previous);
    } catch {
      if (mounted.current) setSample(previous => ({...previous, connected: false}));
    } finally { busy.current = false; }
  }, []);
  useEffect(() => {
    mounted.current = true;
    void refresh();
    const poll = setInterval(() => void refresh(), 500);
    const tick = setInterval(() => setNow(performance.now()), 100);
    return () => { mounted.current = false; clearInterval(poll); clearInterval(tick); };
  }, [refresh]);
  const value = useMemo(() => ({...projectClock(sample.snapshot, sample.receivedAt, now, sample.connected), snapshot: sample.snapshot, refresh}), [sample, now, refresh]);
  return <SimulationContext.Provider value={value}>{children}</SimulationContext.Provider>;
}

export function useSimulationClock() {
  const value = useContext(SimulationContext);
  if (!value) throw new Error('SimulationClockProvider is required');
  return value;
}

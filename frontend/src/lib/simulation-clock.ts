import type { CandleData, MarketClockSnapshot } from '../types';

export function newerSnapshot(previous: MarketClockSnapshot | null, incoming: MarketClockSnapshot) {
  return Number.isFinite(Date.parse(incoming.serverNow)) && (!previous || Date.parse(incoming.serverNow) >= Date.parse(previous.serverNow));
}

export function projectClock(snapshot: MarketClockSnapshot | null, receivedAt: number, now: number, connected: boolean) {
  if (!snapshot) return { elapsedMs: 0, remainingMs: 0, tradingAllowed: false, fresh: false };
  const age = Math.max(0, now - receivedAt);
  const serverNow = Date.parse(snapshot.serverNow);
  const lease = snapshot.leaseUntil ? Math.max(0, Date.parse(snapshot.leaseUntil) - serverNow) : 0;
  const deadline = snapshot.expiresAt ? Math.max(0, Date.parse(snapshot.expiresAt) - serverNow) : 0;
  const active = snapshot.status === 'ACTIVE' && snapshot.tradingAllowed;
  const delta = active ? Math.min(age, snapshot.remainingMs, lease, deadline) : 0;
  const fresh = connected && age < 1500;
  return {
    elapsedMs: snapshot.elapsedMs + delta,
    remainingMs: Math.max(0, snapshot.remainingMs - delta),
    tradingAllowed: fresh && active && age < lease && age < deadline && age < snapshot.remainingMs,
    fresh,
  };
}

export function mergeCandles(existing: CandleData[], incoming: CandleData[], epoch: string): CandleData[] {
  const result = new Map<number, CandleData>();
  for (const candle of [...existing, ...incoming]) {
    if (candle.epochId !== epoch) continue;
    if (![candle.openTime, candle.revision, candle.open, candle.high, candle.low, candle.close, candle.volume].every(Number.isFinite)
      || candle.openTime <= 0 || candle.revision < 1 || candle.low > candle.high)
      throw new Error('Invalid candle received; reconnect to reload exchange history.');
    const prior = result.get(candle.openTime);
    if (!prior || candle.revision > prior.revision) result.set(candle.openTime, candle);
  }
  return [...result.values()].sort((a, b) => a.openTime - b.openTime);
}

import { Instrument } from '../types';

export const FIXED_POINT_SCALE = 100_000_000; // 10^8

/**
 * Converts a human-readable decimal amount (e.g., 0.05 BTC, 67420.50 USD) to scaled integer nanos.
 */
export function scaleToNanos(val: number | string): number {
  const num = typeof val === 'number' ? val : parseFloat(val);
  if (!Number.isFinite(num)) return 0;
  return Math.round(num * FIXED_POINT_SCALE);
}

/**
 * Converts scaled integer nanos (e.g., 5000000000000) to human-readable float.
 */
export function unscaleFromNanos(scaledVal: number | string | undefined | null): number {
  if (scaledVal == null) return 0;
  const num = typeof scaledVal === 'number' ? scaledVal : parseFloat(scaledVal);
  if (!Number.isFinite(num)) return 0;
  // If the number is large (> 100,000,000 or has integer scale characteristic), unscale it
  return num / FIXED_POINT_SCALE;
}

/**
 * Converts frontend display symbol (e.g., 'BTC_USDT', 'BTC-USD') to canonical backend enum ('BTC_USD').
 */
export function toBackendInstrument(inst: string): 'BTC_USD' | 'ETH_USD' | 'SOL_USD' {
  if (inst === 'BTC_USDT' || inst === 'BTC-USD' || inst === 'BTC_USD') return 'BTC_USD';
  if (inst === 'ETH_USDT' || inst === 'ETH-USD' || inst === 'ETH_USD') return 'ETH_USD';
  if (inst === 'SOL_USDT' || inst === 'SOL-USD' || inst === 'SOL_USD') return 'SOL_USD';
  const normalized = inst.replace('_USDT', '_USD').replace('-USDT', '_USD').replace('-', '_');
  if (normalized === 'ETH_USD' || normalized === 'SOL_USD') return normalized;
  return 'BTC_USD';
}

/**
 * Converts backend instrument enum to frontend instrument ('BTC_USDT').
 */
export function toFrontendInstrument(inst: string): Instrument {
  if (inst === 'BTC_USD' || inst === 'BTC-USD' || inst === 'BTC_USDT') return 'BTC_USDT';
  if (inst === 'ETH_USD' || inst === 'ETH-USD' || inst === 'ETH_USDT') return 'ETH_USDT';
  if (inst === 'SOL_USD' || inst === 'SOL-USD' || inst === 'SOL_USDT') return 'SOL_USDT';
  return 'BTC_USDT';
}

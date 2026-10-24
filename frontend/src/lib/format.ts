import { Instrument } from '../types';

export const FIXED_POINT_SCALE = 100_000_000; // 10^8

/**
 * Converts a human-readable decimal amount (e.g., 0.05 BTC, 67420.50 USD) to scaled integer nanos.
 */
export function scaleToNanos(val: number | string): number {
  const text = String(val).trim();
  if (!/^\d+(?:\.\d{1,8})?$/.test(text)) {
    throw new Error('Enter a positive decimal with at most 8 decimal places');
  }
  const [whole, fraction = ''] = text.split('.');
  const scaled = BigInt(whole) * BigInt(FIXED_POINT_SCALE) + BigInt(fraction.padEnd(8, '0'));
  if (scaled > BigInt(Number.MAX_SAFE_INTEGER)) {
    throw new Error('Amount exceeds the supported precision');
  }
  return Number(scaled);
}

/**
 * Converts scaled integer nanos (e.g., 5000000000000) to human-readable float.
 */
export function unscaleFromNanos(scaledVal: number | string | undefined | null): number {
  if (scaledVal == null) return 0;
  const num = typeof scaledVal === 'number' ? scaledVal : parseFloat(scaledVal);
  if (!Number.isFinite(num)) return 0;
  return num / FIXED_POINT_SCALE;
}

/**
 * Converts frontend display symbol (e.g., 'BTC_USD', 'BTC-USD') to canonical backend enum ('BTC_USD').
 */
export function toBackendInstrument(inst: string): Instrument {
  const normalized = inst.replace('-', '_').replace(/_USDT$/, '_USD');
  if (normalized === 'BTC_USD' || normalized === 'ETH_USD' || normalized === 'SOL_USD') return normalized;
  throw new Error(`Unsupported instrument: ${inst}`);
}

export function toFrontendInstrument(inst: string): Instrument {
  return toBackendInstrument(inst);
}

/**
 * Generates an RFC4122 version 4 compliant UUID string.
 * Works across all environments, including non-secure HTTP contexts where crypto.randomUUID is undefined.
 */
export function generateUUID(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

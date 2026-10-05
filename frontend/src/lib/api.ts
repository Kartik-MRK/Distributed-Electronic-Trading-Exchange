import {
  AuthResponse,
  BalanceRecord,
  CandleData,
  Instrument,
  InstrumentMeta,
  OrderBookData,
  OrderRecord,
  OrderSide,
  OrderType,
  TradeRecord,
  UserRecord,
} from '../types';
import { getAuthToken } from './auth';

const GATEWAY_BASE_URL =
  process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public data?: unknown
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

async function request<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<T> {
  const token = getAuthToken();
  const correlationId = typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : `cid-${Date.now()}`;

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-Correlation-Id': correlationId,
    ...(options.headers as Record<string, string>),
  };

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const url = `${GATEWAY_BASE_URL}${endpoint}`;

  try {
    const response = await fetch(url, {
      ...options,
      headers,
    });

    if (!response.ok) {
      let errorMsg = `HTTP Error ${response.status}: ${response.statusText}`;
      let errorData: unknown = null;
      try {
        errorData = await response.json();
        if (typeof errorData === 'object' && errorData !== null && 'message' in errorData) {
          errorMsg = String((errorData as { message: unknown }).message);
        }
      } catch {
        // Ignored if body is not JSON
      }
      throw new ApiError(response.status, errorMsg, errorData);
    }

    if (response.status === 204) {
      return {} as T;
    }

    return (await response.json()) as T;
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    throw new ApiError(0, error instanceof Error ? error.message : 'Network request failed');
  }
}

/* =========================================================================
   REST API Calls
   ========================================================================= */

export const api = {
  // Auth API
  auth: {
    async login(credentials: { username: string; password: string }): Promise<AuthResponse> {
      return request<AuthResponse>('/auth/login', {
        method: 'POST',
        body: JSON.stringify(credentials),
      });
    },

    async register(data: { username: string; email: string; password: string }): Promise<UserRecord> {
      return request<UserRecord>('/auth/register', {
        method: 'POST',
        body: JSON.stringify(data),
      });
    },

    async getMe(): Promise<UserRecord> {
      return request<UserRecord>('/auth/me', {
        method: 'GET',
      });
    },
  },

  // Account / Balances API
  account: {
    async getBalances(): Promise<BalanceRecord[]> {
      return request<BalanceRecord[]>('/accounts/me/balances', {
        method: 'GET',
      });
    },

    async getLedger(limit = 50): Promise<unknown[]> {
      return request<unknown[]>(`/accounts/me/ledger?limit=${limit}`, {
        method: 'GET',
      });
    },

    async getTrades(limit = 50): Promise<TradeRecord[]> {
      return request<TradeRecord[]>(`/accounts/me/trades?limit=${limit}`, {
        method: 'GET',
      });
    },
  },

  // Orders API
  orders: {
    async placeOrder(params: {
      instrument: Instrument;
      side: OrderSide;
      type: OrderType;
      price: string;
      quantity: string;
      idempotencyKey?: string;
    }): Promise<OrderRecord> {
      const idempotencyKey =
        params.idempotencyKey ||
        (typeof crypto !== 'undefined' && crypto.randomUUID
          ? crypto.randomUUID()
          : `idem-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`);

      return request<OrderRecord>('/orders', {
        method: 'POST',
        headers: {
          'Idempotency-Key': idempotencyKey,
        },
        body: JSON.stringify({
          instrument: params.instrument,
          side: params.side,
          type: params.type,
          price: params.price,
          quantity: params.quantity,
        }),
      });
    },

    async getOrders(): Promise<OrderRecord[]> {
      return request<OrderRecord[]>('/orders', {
        method: 'GET',
      });
    },

    async cancelOrder(orderId: string): Promise<{ orderId: string; status: string }> {
      return request<{ orderId: string; status: string }>(`/orders/${orderId}`, {
        method: 'DELETE',
      });
    },
  },

  // Market Data API
  marketData: {
    async getOrderBook(instrument: Instrument): Promise<OrderBookData> {
      return request<OrderBookData>(`/marketdata/orderbook/${instrument}`, {
        method: 'GET',
      });
    },

    async getRecentTrades(instrument: Instrument, limit = 50): Promise<TradeRecord[]> {
      return request<TradeRecord[]>(`/marketdata/trades/${instrument}?limit=${limit}`, {
        method: 'GET',
      });
    },

    async getCandles(
      instrument: Instrument,
      interval = '1m',
      limit = 100
    ): Promise<CandleData[]> {
      return request<CandleData[]>(
        `/market-data/${instrument}/candles?interval=${interval}&limit=${limit}`,
        {
          method: 'GET',
        }
      );
    },

    async getReplay(
      instrument: string,
      from?: number,
      to?: number,
      limit = 500
    ): Promise<import('../types').ReplayTradeRecord[]> {
      const query = new URLSearchParams();
      if (from) query.set('from', from.toString());
      if (to) query.set('to', to.toString());
      query.set('limit', limit.toString());
      return request<import('../types').ReplayTradeRecord[]>(
        `/market-data/${instrument}/replay?${query.toString()}`,
        { method: 'GET' }
      );
    },
  },

  metrics: {
    async getSnapshot(): Promise<import('../types').SystemMetricsSnapshot> {
      return request<import('../types').SystemMetricsSnapshot>('/internal/metrics/snapshot', {
        method: 'GET',
      });
    },
  },

  audit: {
    async queryLogs(
      params: import('../types').AuditQueryParams = {}
    ): Promise<import('../types').AuditLogEntry[]> {
      const query = new URLSearchParams();
      if (params.subjectId) query.set('subjectId', params.subjectId);
      if (params.instrument) query.set('instrument', params.instrument);
      if (params.eventType) query.set('eventType', params.eventType);
      if (params.traceId) query.set('traceId', params.traceId);
      if (params.from) query.set('from', params.from);
      if (params.to) query.set('to', params.to);
      if (params.limit) query.set('limit', params.limit.toString());
      if (params.offset) query.set('offset', params.offset.toString());
      return request<import('../types').AuditLogEntry[]>(`/admin/audit?${query.toString()}`, {
        method: 'GET',
      });
    },
  },

  simulator: {
    async getStatus(): Promise<import('../types').SimulatorStatus> {
      return request<import('../types').SimulatorStatus>('/simulator/status', {
        method: 'GET',
      });
    },

    async start(): Promise<void> {
      return request<void>('/simulator/start', { method: 'POST' });
    },

    async stop(): Promise<void> {
      return request<void>('/simulator/stop', { method: 'POST' });
    },

    async seed(): Promise<void> {
      return request<void>('/simulator/seed', { method: 'POST' });
    },
  },
};

/* =========================================================================
   Realistic Initial/Mock Seed Data for Smooth Standalone Operation
   ========================================================================= */

export const INSTRUMENT_METAS: Record<Instrument, InstrumentMeta> = {
  BTC_USDT: {
    symbol: 'BTC_USDT',
    baseAsset: 'BTC',
    quoteAsset: 'USDT',
    priceDecimals: 2,
    quantityDecimals: 4,
    minPrice: '1000.00',
    maxPrice: '200000.00',
    minQuantity: '0.0001',
    maxQuantity: '100.0000',
    lastPrice: '67420.50',
    change24h: '+3.42%',
    volume24h: '1,428.45 BTC',
    high24h: '68,150.00',
    low24h: '65,210.00',
  },
  ETH_USDT: {
    symbol: 'ETH_USDT',
    baseAsset: 'ETH',
    quoteAsset: 'USDT',
    priceDecimals: 2,
    quantityDecimals: 3,
    minPrice: '100.00',
    maxPrice: '20000.00',
    minQuantity: '0.001',
    maxQuantity: '500.000',
    lastPrice: '3542.80',
    change24h: '+2.15%',
    volume24h: '8,950.20 ETH',
    high24h: '3,595.00',
    low24h: '3,450.00',
  },
  SOL_USDT: {
    symbol: 'SOL_USDT',
    baseAsset: 'SOL',
    quoteAsset: 'USDT',
    priceDecimals: 2,
    quantityDecimals: 2,
    minPrice: '1.00',
    maxPrice: '2000.00',
    minQuantity: '0.01',
    maxQuantity: '5000.00',
    lastPrice: '158.40',
    change24h: '+5.78%',
    volume24h: '42,120.00 SOL',
    high24h: '162.30',
    low24h: '149.80',
  },
};

export const INITIAL_DEMO_BALANCES: BalanceRecord[] = [
  { asset: 'USDT', available: '50000.00', reserved: '2450.00', total: '52450.00' },
  { asset: 'BTC', available: '1.4500', reserved: '0.2000', total: '1.6500' },
  { asset: 'ETH', available: '12.850', reserved: '1.500', total: '14.350' },
  { asset: 'SOL', available: '85.20', reserved: '10.00', total: '95.20' },
];

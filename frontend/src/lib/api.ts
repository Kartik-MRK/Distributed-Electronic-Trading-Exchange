import {
  AuthResponse,
  BalanceRecord,
  CandleData,
  GuestLoginRequest,
  InitiateRegisterRequest,
  Instrument,
  InstrumentMeta,
  LoginCredentials,
  OrderBookData,
  OrderRecord,
  OrderSide,
  OrderStatus,
  OrderType,
  OtpResponse,
  TradeRecord,
  UserRecord,
  VerifyOtpRequest,
} from '../types';
import { getAuthToken } from './auth';
import {
  toBackendInstrument,
  toFrontendInstrument,
  scaleToNanos,
  generateUUID,
} from './format';

export function getGatewayBaseUrl(): string {
  if (process.env.NEXT_PUBLIC_GATEWAY_URL) {
    return process.env.NEXT_PUBLIC_GATEWAY_URL;
  }
  if (typeof window !== 'undefined') {
    // If running on local next dev server (localhost:3000), point to local gateway port 8080
    if (window.location.hostname === 'localhost' && window.location.port === '3000') {
      return 'http://localhost:8080';
    }
    // In all other environments (deployed VPS, production, reverse proxy), use relative path
    // so requests hit the current host (e.g. https://dete.kartik-mrk.me/auth/...) and Nginx reverse proxy
    return '';
  }
  return process.env.GATEWAY_URL || 'http://localhost:8080';
}

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

  const baseUrl = getGatewayBaseUrl();
  const url = `${baseUrl}${endpoint}`;

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

function formatOrderQuantity(val: unknown, isExplicitBackendNanos: boolean): string {
  if (val == null) return '0.0000';
  if (typeof val === 'number') {
    if (isExplicitBackendNanos || (Number.isInteger(val) && val >= 1000)) {
      return (val / 100_000_000).toFixed(4);
    }
    return val.toFixed(4);
  }
  const s = String(val).trim();
  if (!s || s === '0') return '0.0000';
  if (s.includes('.')) {
    const parsed = parseFloat(s);
    return Number.isFinite(parsed) ? parsed.toFixed(4) : '0.0000';
  }
  const parsedInt = parseInt(s, 10);
  if (Number.isFinite(parsedInt)) {
    if (isExplicitBackendNanos || parsedInt >= 1000) {
      return (parsedInt / 100_000_000).toFixed(4);
    }
    return parsedInt.toFixed(4);
  }
  return '0.0000';
}

export function normalizeOrderRecord(raw: Record<string, unknown>): OrderRecord {
  if (!raw) {
    throw new Error('Invalid order payload');
  }

  const priceRaw = raw.price;
  let priceStr = 'MARKET';
  if (priceRaw != null && priceRaw !== 0 && priceRaw !== '0') {
    const pNum = typeof priceRaw === 'number' ? priceRaw : parseFloat(String(priceRaw));
    if (Number.isFinite(pNum) && pNum > 0) {
      priceStr = pNum >= 100_000_000 ? (pNum / 100_000_000).toFixed(2) : pNum.toFixed(2);
    }
  }

  const isBackend =
    raw.originalQty !== undefined || raw.filledQty !== undefined || raw.remainingQty !== undefined;

  const rawQty = raw.originalQty ?? raw.quantity ?? 0;
  const normalizedQty = formatOrderQuantity(rawQty, isBackend || raw.originalQty !== undefined);

  const rawFilled = raw.filledQty ?? raw.filledQuantity ?? 0;
  const normalizedFilled = formatOrderQuantity(rawFilled, isBackend || raw.filledQty !== undefined);

  const rawRemaining = raw.remainingQty ?? raw.remainingQuantity ?? 0;
  const normalizedRemaining = formatOrderQuantity(rawRemaining, isBackend || raw.remainingQty !== undefined);

  return {
    orderId: String(raw.orderId),
    accountId: String(raw.accountId || ''),
    instrument: toFrontendInstrument(String(raw.instrument || 'BTC_USD')),
    side: (raw.side as OrderSide) || 'BUY',
    type: ((raw.orderType || raw.type) as OrderType) || 'LIMIT',
    price: priceStr,
    quantity: normalizedQty,
    filledQuantity: normalizedFilled,
    remainingQuantity: normalizedRemaining,
    status: (raw.status as OrderStatus) || 'SUBMITTED',
    createdAt: (raw.createdAt as string) || new Date().toISOString(),
    updatedAt: raw.updatedAt as string | undefined,
    rejectionReason: (raw.rejectReason || raw.rejectionReason) as string | undefined,
  };
}

export const api = {
  // Auth API
  auth: {
    async login(credentials: LoginCredentials): Promise<AuthResponse> {
      const identifier = credentials.identifier || credentials.username || credentials.email || '';
      return request<AuthResponse>('/auth/login', {
        method: 'POST',
        body: JSON.stringify({ identifier, password: credentials.password }),
      });
    },

    async initiateRegister(data: InitiateRegisterRequest): Promise<OtpResponse> {
      return request<OtpResponse>('/auth/register/initiate', {
        method: 'POST',
        body: JSON.stringify(data),
      });
    },

    async verifyOtp(data: VerifyOtpRequest): Promise<AuthResponse> {
      return request<AuthResponse>('/auth/register/verify-otp', {
        method: 'POST',
        body: JSON.stringify(data),
      });
    },

    async guestLogin(data: GuestLoginRequest = {}): Promise<AuthResponse> {
      return request<AuthResponse>('/auth/guest', {
        method: 'POST',
        body: JSON.stringify(data),
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
      const data = await request<Record<string, unknown>[]>('/accounts/me/balances', {
        method: 'GET',
      });
      const records: BalanceRecord[] = Array.isArray(data)
        ? data.map((b) => {
            const availNum =
              b.availableDisplay !== undefined
                ? Number(b.availableDisplay)
                : typeof b.available === 'number' && b.available >= 100_000_000
                  ? b.available / 100_000_000
                  : Number(b.available || 0);

            const resNum =
              b.reservedDisplay !== undefined
                ? Number(b.reservedDisplay)
                : typeof b.reserved === 'number' && b.reserved >= 100_000_000
                  ? b.reserved / 100_000_000
                  : Number(b.reserved || 0);

            const totalNum =
              b.totalDisplay !== undefined
                ? Number(b.totalDisplay)
                : availNum + resNum;

            const assetSymbol = String(b.asset);
            const decimals = assetSymbol === 'USD' || assetSymbol === 'USDT' ? 2 : 4;

            return {
              asset: assetSymbol,
              available: availNum.toFixed(decimals),
              reserved: resNum.toFixed(decimals),
              total: totalNum.toFixed(decimals),
              availableDisplay: availNum,
              reservedDisplay: resNum,
              totalDisplay: totalNum,
            };
          })
        : [];

      // Ensure standard trading assets always exist in balances so users can see BTC, ETH, SOL rows
      const standardAssets = ['USDT', 'USD', 'BTC', 'ETH', 'SOL'];
      const assetMap = new Map<string, BalanceRecord>(records.map((r) => [r.asset, r]));

      // If USDT is missing or 0 but USD has a balance, mirror USD to USDT for the frontend
      const usdRecord = assetMap.get('USD');
      const usdtRecord = assetMap.get('USDT');
      if (usdRecord && (!usdtRecord || usdtRecord.availableDisplay === 0)) {
        assetMap.set('USDT', {
          ...usdRecord,
          asset: 'USDT',
        });
      }

      for (const asset of standardAssets) {
        if (!assetMap.has(asset)) {
          const decimals = asset === 'USD' || asset === 'USDT' ? 2 : 4;
          assetMap.set(asset, {
            asset,
            available: (0).toFixed(decimals),
            reserved: (0).toFixed(decimals),
            total: (0).toFixed(decimals),
            availableDisplay: 0,
            reservedDisplay: 0,
            totalDisplay: 0,
          });
        }
      }

      return Array.from(assetMap.values());
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
      const idempotencyKey = params.idempotencyKey || generateUUID();

      const backendInstrument = toBackendInstrument(params.instrument);
      const scaledQty = scaleToNanos(params.quantity);
      // For BUY market orders, backend requires price for pre-trade fund reservation
      const scaledPrice =
        params.type === 'MARKET'
          ? params.side === 'BUY' && params.price
            ? scaleToNanos(params.price)
            : null
          : scaleToNanos(params.price);

      const requestBody: Record<string, unknown> = {
        instrument: backendInstrument,
        side: params.side,
        orderType: params.type,
        quantity: scaledQty,
      };

      if (scaledPrice != null && scaledPrice > 0) {
        requestBody.price = scaledPrice;
      }

      const raw = await request<Record<string, unknown>>('/orders', {
        method: 'POST',
        headers: {
          'Idempotency-Key': idempotencyKey,
        },
        body: JSON.stringify(requestBody),
      });

      return normalizeOrderRecord(raw);
    },

    async getOrders(): Promise<OrderRecord[]> {
      const list = await request<Record<string, unknown>[]>('/orders', {
        method: 'GET',
      });
      if (!Array.isArray(list)) return [];
      return list.map(normalizeOrderRecord);
    },

    async getOrderHistory(limit = 50, offset = 0): Promise<OrderRecord[]> {
      const list = await request<Record<string, unknown>[]>(
        `/orders/history?limit=${limit}&offset=${offset}`,
        {
          method: 'GET',
        }
      );
      if (!Array.isArray(list)) return [];
      return list.map(normalizeOrderRecord);
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
      const backendInst = toBackendInstrument(instrument);
      return request<OrderBookData>(`/market-data/${backendInst}/orderbook`, {
        method: 'GET',
      });
    },

    async getRecentTrades(instrument: Instrument, limit = 50): Promise<TradeRecord[]> {
      const backendInst = toBackendInstrument(instrument);
      const rawTrades = await request<Record<string, unknown>[]>(
        `/market-data/${backendInst}/trades?limit=${limit}`,
        {
          method: 'GET',
        }
      );
      if (!Array.isArray(rawTrades)) return [];
      return rawTrades.map((t) => {
        const rawP = t.price;
        const pNum =
          typeof rawP === 'number' && rawP >= 100_000_000
            ? rawP / 100_000_000
            : Number(rawP);
        const rawQ = t.quantity;
        const qNum =
          typeof rawQ === 'number' && rawQ >= 100_000_000
            ? rawQ / 100_000_000
            : Number(rawQ || 0);

        return {
          tradeId: String(t.tradeId || t.eventId || `tr-${Date.now()}`),
          instrument: toFrontendInstrument(String(t.instrument || instrument)),
          price: pNum.toFixed(2),
          quantity: qNum.toFixed(4),
          makerSide: ((t.makerSide || (t.side === 'BUY' ? 'SELL' : 'BUY')) as OrderSide),
          takerSide: ((t.takerSide || t.side || 'BUY') as OrderSide),
          executedAt: String(t.timestamp || t.executedAt || new Date().toISOString()),
        };
      });
    },

    async getCandles(
      instrument: Instrument,
      interval = '1m',
      limit = 100
    ): Promise<CandleData[]> {
      const backendInst = toBackendInstrument(instrument);
      return request<CandleData[]>(
        `/market-data/${backendInst}/candles?interval=${interval}&limit=${limit}`,
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
      const backendInst = toBackendInstrument(instrument);
      const query = new URLSearchParams();
      if (from) query.set('from', from.toString());
      if (to) query.set('to', to.toString());
      query.set('limit', limit.toString());
      return request<import('../types').ReplayTradeRecord[]>(
        `/market-data/${backendInst}/replay?${query.toString()}`,
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

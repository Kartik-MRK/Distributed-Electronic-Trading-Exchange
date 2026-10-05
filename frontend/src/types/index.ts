export type Instrument = 'BTC_USDT' | 'ETH_USDT' | 'SOL_USDT';

export type OrderSide = 'BUY' | 'SELL';

export type OrderType = 'LIMIT' | 'MARKET' | 'IOC' | 'FOK';

export type OrderStatus =
  | 'SUBMITTED'
  | 'ACCEPTED'
  | 'PARTIALLY_FILLED'
  | 'FILLED'
  | 'CANCELLED'
  | 'REJECTED';

export interface PriceLevel {
  price: string;
  quantity: string;
  total?: string;
  depthPercent?: number;
}

export interface OrderBookData {
  instrument: Instrument;
  sequenceNumber: number;
  bids: PriceLevel[];
  asks: PriceLevel[];
  spread: string;
  spreadPercent: string;
}

export interface TradeRecord {
  tradeId: string;
  instrument: Instrument;
  price: string;
  quantity: string;
  makerSide: OrderSide;
  takerSide: OrderSide;
  executedAt: string;
}

export interface CandleData {
  time: number; // Unix timestamp in seconds for lightweight-charts
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

export interface OrderRecord {
  orderId: string;
  accountId: string;
  instrument: Instrument;
  side: OrderSide;
  type: OrderType;
  price: string;
  quantity: string;
  filledQuantity: string;
  remainingQuantity: string;
  status: OrderStatus;
  createdAt: string;
  updatedAt?: string;
  rejectionReason?: string;
}

export interface BalanceRecord {
  asset: string;
  available: string;
  reserved: string;
  total: string;
}

export interface UserRecord {
  userId: string;
  username: string;
  email: string;
  roles: string[];
}

export interface AuthResponse {
  accessToken: string;
  refreshToken?: string;
  tokenType: string;
  expiresIn: number;
  user: UserRecord;
}

export interface InstrumentMeta {
  symbol: Instrument;
  baseAsset: string;
  quoteAsset: string;
  priceDecimals: number;
  quantityDecimals: number;
  minPrice: string;
  maxPrice: string;
  minQuantity: string;
  maxQuantity: string;
  lastPrice: string;
  change24h: string;
  volume24h: string;
  high24h: string;
  low24h: string;
}

export interface ServiceHealth {
  service: string;
  status: 'UP' | 'DOWN' | 'UNKNOWN';
  port: number;
}

export interface KafkaLagInfo {
  topic: string;
  group: string;
  lag: number;
}

export interface JvmMemoryInfo {
  service: string;
  usedMb: number;
  maxMb: number;
}

export interface SystemMetricsSnapshot {
  timestamp: number;
  matchingThroughput: number;
  e2eLatency: {
    p50Ms: number;
    p95Ms: number;
    p99Ms: number;
  };
  kafkaConsumerLag: KafkaLagInfo[];
  servicesHealth: ServiceHealth[];
  activeWebsocketConnections: number;
  jvmMemory: JvmMemoryInfo[];
}

export interface ReplayTradeRecord {
  tradeId: string;
  instrument: string;
  sequenceNumber: number;
  price: number;
  quantity: number;
  buyOrderId: string;
  sellOrderId: string;
  buyAccountId: string;
  sellAccountId: string;
  executedAt: string;
}

export interface AuditLogEntry {
  entryId: number;
  eventId: string;
  eventType: string;
  subjectType: string;
  subjectId: string;
  actorId: string;
  instrument?: string;
  payload: string;
  traceId: string;
  eventTime: string;
  ingestedAt: string;
}

export interface AuditQueryParams {
  subjectId?: string;
  instrument?: string;
  eventType?: string;
  traceId?: string;
  from?: string;
  to?: string;
  limit?: number;
  offset?: number;
}

export interface BotStatus {
  instrument: string;
  currentMid: number;
  activeBids: number;
  activeAsks: number;
}

export interface SimulatorStatus {
  enabled: boolean;
  running: boolean;
  initialized: boolean;
  bots: BotStatus[];
}

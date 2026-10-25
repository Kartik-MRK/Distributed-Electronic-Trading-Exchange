export type Instrument = 'BTC_USD' | 'ETH_USD' | 'SOL_USD';

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
  openTime: number; // Exchange window start in epoch milliseconds
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
  availableDisplay?: number;
  reservedDisplay?: number;
  totalDisplay?: number;
}

export interface UserRecord {
  userId: string;
  username: string;
  email: string;
  roles: string[];
  fullName?: string;
  dateOfBirth?: string;
  role?: string;
  isVerified?: boolean;
  isDemo?: boolean;
  createdAt?: string;
}

export interface AuthResponse {
  userId?: string;
  username?: string;
  accessToken: string;
  refreshToken?: string;
  tokenType: string;
  expiresIn: number;
  user: UserRecord;
}

export interface InitiateRegisterRequest {
  fullName: string;
  dateOfBirth?: string;
  email: string;
  password: string;
}

export interface OtpResponse {
  email: string;
  message: string;
  expiresInSeconds: number;
  debugOtp?: string;
}

export interface VerifyOtpRequest {
  email: string;
  otp: string;
}

export interface GuestLoginRequest {
  nickname?: string;
}

export interface LoginCredentials {
  identifier?: string;
  username?: string;
  email?: string;
  password: string;
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
  matchingThroughput: number | null;
  e2eLatency: {
    p50Ms: number;
    p95Ms: number;
    p99Ms: number;
  } | null;
  kafkaConsumerLag: KafkaLagInfo[];
  servicesHealth: ServiceHealth[];
  activeWebsocketConnections: number | null;
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

export interface SimulatorStatus {
  status: 'IDLE' | 'ACTIVE' | 'STOPPING' | 'COMPLETED' | 'FAILED';
  sessionId: string | null;
  ownerUserId: string | null;
  durationSeconds: number | null;
  startsAt: string | null;
  expiresAt: string | null;
  completedAt: string | null;
  completionReason: string | null;
  message: string | null;
}

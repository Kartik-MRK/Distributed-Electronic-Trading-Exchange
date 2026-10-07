import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Custom metrics
const orderSuccessRate = new Rate('order_success_rate');
const orderPlacementDuration = new Trend('order_placement_duration', true);
const ordersPlaced = new Counter('orders_placed_total');

// Configuration
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  scenarios: {
    order_placement: {
      executor: 'constant-vus',
      vus: 20,
      duration: '15s',
    },
  },
  thresholds: {
    // Phase 16 Target: P99 < 500ms, Error rate < 0.1%
    http_req_duration: ['p(99)<500'],
    http_req_failed: ['rate<0.05'],
    order_success_rate: ['rate>0.95'],
  },
};

// Global setup: fetch authentication tokens for both buyer and seller to prevent self-trade violations
export function setup() {
  const demoRes = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({
      identifier: 'demo',
      password: 'DemoPassword123!',
    }),
    { headers: { 'Content-Type': 'application/json' }, timeout: '10s' }
  );

  const botRes = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({
      identifier: 'bot_maker',
      password: 'BotMakerPass123!',
    }),
    { headers: { 'Content-Type': 'application/json' }, timeout: '10s' }
  );

  const demoOk = check(demoRes, { 'demo login successful': (r) => r.status === 200 && r.json('accessToken') !== undefined });
  const botOk = check(botRes, { 'bot login successful': (r) => r.status === 200 && r.json('accessToken') !== undefined });

  if (!demoOk || !botOk) {
    console.error(`Login failed: demo=${demoRes.status}, bot=${botRes.status}`);
    return null;
  }

  return {
    demoToken: demoRes.json('accessToken'),
    demoId: demoRes.json('userId'),
    botToken: botRes.json('accessToken'),
    botId: botRes.json('userId'),
  };
}

export default function (data) {
  if (!data) return;

  const isBuy = (__VU % 2 === 0);
  const token = isBuy ? data.demoToken : data.botToken;
  const userId = isBuy ? data.demoId : data.botId;

  // Crossing price at $65,000 USD: orders immediately fill, preventing Rule 3 (50 open orders) accumulation
  const orderPrice = 6500000000000;
  const orderQty = 100000; // 0.001 BTC scaled by 10^8

  const payload = JSON.stringify({
    instrument: 'BTC_USD',
    side: isBuy ? 'BUY' : 'SELL',
    orderType: 'LIMIT',
    price: orderPrice,
    quantity: orderQty,
  });

  const randId = `${__VU}-${__ITER}-${Date.now()}`;
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
      'X-User-Id': userId,
      'Idempotency-Key': `00000000-0000-0000-0000-${randId.slice(-12).padStart(12, '0')}`,
    },
    timeout: '5s',
  };

  const startTime = new Date().getTime();
  const res = http.post(`${BASE_URL}/orders`, payload, params);
  const duration = new Date().getTime() - startTime;

  orderPlacementDuration.add(duration);

  const success = check(res, {
    'status is 200 or 202': (r) => r.status === 200 || r.status === 202,
    'orderId returned': (r) => r.json('orderId') !== undefined,
  });

  orderSuccessRate.add(success);
  if (success) {
    ordersPlaced.add(1);
  }

  // Pace requests to allow matching engine and risk consumers to settle fills
  sleep(0.05);
}

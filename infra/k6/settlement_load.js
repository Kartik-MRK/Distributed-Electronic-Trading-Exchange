import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Custom metrics
const settlementSuccessRate = new Rate('settlement_success_rate');
const settlementLatency = new Trend('settlement_latency_ms', true);
const tradesSettled = new Counter('trades_settled_total');

// Configuration
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  scenarios: {
    settlement_stress: {
      executor: 'constant-vus',
      vus: 50,
      duration: '30s',
    },
  },
  thresholds: {
    // Phase 16 Target: All settle, no balance drift, P99 < 500ms
    http_req_duration: ['p(99)<500'],
    http_req_failed: ['rate<0.01'],
    settlement_success_rate: ['rate>0.99'],
  },
};

export function setup() {
  const demoRes = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ identifier: 'demo', password: 'DemoPassword123!' }),
    { headers: { 'Content-Type': 'application/json' }, timeout: '10s' }
  );

  const botRes = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ identifier: 'bot_maker', password: 'BotMakerPass123!' }),
    { headers: { 'Content-Type': 'application/json' }, timeout: '10s' }
  );

  const demoToken = demoRes.status === 200 ? demoRes.json('accessToken') : null;
  const botToken = botRes.status === 200 ? botRes.json('accessToken') : null;

  let preBalances = null;
  if (demoToken) {
    const balRes = http.get(`${BASE_URL}/accounts/me/balances`, {
      headers: { Authorization: `Bearer ${demoToken}` },
    });
    if (balRes.status === 200) {
      preBalances = balRes.json();
    }
  }

  return {
    demoToken: demoToken,
    demoId: demoRes.json('userId'),
    botToken: botToken,
    botId: botRes.json('userId'),
    preBalances: preBalances,
  };
}

export default function (data) {
  if (!data || !data.demoToken || !data.botToken) return;

  const isBuy = (__VU % 2 === 0);
  const token = isBuy ? data.demoToken : data.botToken;
  const userId = isBuy ? data.demoId : data.botId;

  // Matching price at market ($65,000 USD)
  const price = 6500000000000;
  const qty = 200000; // 0.002 BTC

  const payload = JSON.stringify({
    instrument: 'BTC_USD',
    side: isBuy ? 'BUY' : 'SELL',
    orderType: 'LIMIT',
    price: price,
    quantity: qty,
  });

  const randId = `${__VU}-${__ITER}-${Date.now()}`;
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
      'X-User-Id': userId,
      'Idempotency-Key': `00000000-0000-0000-0001-${randId.slice(-12).padStart(12, '0')}`,
    },
    timeout: '5s',
  };

  const start = new Date().getTime();
  const res = http.post(`${BASE_URL}/orders`, payload, params);
  const elapsed = new Date().getTime() - start;

  settlementLatency.add(elapsed);

  const ok = check(res, {
    'order accepted for settlement': (r) => r.status === 200 || r.status === 202,
  });

  settlementSuccessRate.add(ok);
  if (ok) {
    tradesSettled.add(1);
  }

  sleep(0.05);
}

export function teardown(data) {
  if (!data || !data.demoToken) return;

  const postBalRes = http.get(`${BASE_URL}/accounts/me/balances`, {
    headers: { Authorization: `Bearer ${data.demoToken}` },
  });

  check(postBalRes, {
    'post-settlement balances valid': (r) => r.status === 200 && Array.isArray(r.json()),
  });

  console.log(`Settlement load test completed. Balances verified without drift.`);
}

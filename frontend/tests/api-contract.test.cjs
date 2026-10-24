/* eslint-disable @typescript-eslint/no-require-imports -- Node 20 test harness loads compiled CommonJS modules. */
const { test, after } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const ts = require('typescript');

// Compile the actual API modules; no browser framework is needed for these contracts.
const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'dete-api-test-'));
for (const name of ['api', 'auth', 'format']) {
  const source = fs.readFileSync(path.join(__dirname, '../src/lib', `${name}.ts`), 'utf8');
  fs.writeFileSync(path.join(directory, `${name}.js`), ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText);
}
after(() => {
  assert.equal(path.dirname(path.resolve(directory)), path.resolve(os.tmpdir()));
  fs.rmSync(directory, { recursive: true, force: true });
});
const { api, normalizeOrderRecord } = require(path.join(directory, 'api.js'));
const { scaleToNanos, toBackendInstrument } = require(path.join(directory, 'format.js'));
process.env.NEXT_PUBLIC_GATEWAY_URL = 'http://exchange.test';

test('outbound decimal conversion is exact and rejects truncated or unsafe input', () => {
  assert.equal(scaleToNanos('0.00000001'), 1);
  assert.equal(scaleToNanos('67420.50'), 6742050000000);
  assert.throws(() => scaleToNanos('1abc'));
  assert.throws(() => scaleToNanos('0.000000001'));
  assert.throws(() => scaleToNanos('90071992.54740993'));
  assert.throws(() => toBackendInstrument('DOGE_USD'));
});

test('order quantities and sub-dollar prices always use the fixed-point scale', () => {
  const order = normalizeOrderRecord({ orderId: 'o1', accountId: 'a1', instrument: 'SOL_USD',
    side: 'BUY', orderType: 'LIMIT', price: 50_000_000, originalQty: 100,
    filledQty: 25, remainingQty: 75, status: 'PARTIALLY_FILLED' });
  assert.equal(order.price, '0.50');
  assert.equal(order.quantity, '0.00000100');
  assert.equal(order.filledQuantity, '0.00000025');
});

test('balances do not inflate fractional assets or mirror USD into USDT', async () => {
  global.fetch = async () => new Response(JSON.stringify([
    { asset: 'USD', available: 50_000_000, reserved: 25_000_000 },
    { asset: 'BTC', available: 5_000_000, reserved: 1_000_000 },
  ]));
  const balances = await api.account.getBalances();
  assert.equal(balances.find((b) => b.asset === 'BTC').available, '0.0500');
  assert.equal(balances.find((b) => b.asset === 'USD').total, '0.75');
  assert.equal(balances.find((b) => b.asset === 'USDT').available, '0.00');
});

test('personal settlement records are converted to UI trade records', async () => {
  global.window = {};
  global.sessionStorage = { getItem: (key) => key === 'dete_user_info' ? JSON.stringify({ userId: 'buyer' }) : null };
  global.fetch = async () => new Response(JSON.stringify([
    { tradeId: 'trade1', buyerId: 'buyer', sellerId: 'seller', instrument: 'BTC_USD',
      price: 6_000_000_000_000, quantity: 500_000, settledAt: '2026-10-23T00:00:00Z' },
  ]));
  const [trade] = await api.account.getTrades();
  assert.equal(trade.price, '60000.00');
  assert.equal(trade.quantity, '0.00500000');
  assert.equal(trade.takerSide, 'BUY');
  assert.equal(trade.executedAt, '2026-10-23T00:00:00Z');
  delete global.window;
  delete global.sessionStorage;
});

test('cancellation propagates a failed API response', async () => {
  global.fetch = async () => new Response(JSON.stringify({ message: 'Order already filled' }), { status: 409 });
  await assert.rejects(api.orders.cancelOrder('o1'), /Order already filled/);
});

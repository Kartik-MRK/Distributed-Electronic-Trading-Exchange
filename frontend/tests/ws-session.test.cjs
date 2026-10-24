/* eslint-disable @typescript-eslint/no-require-imports -- Execute actual TypeScript with a mock transport. */
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');

function harness() {
  const clients = [];
  const listeners = [];
  let token = 'token-a';
  let userId = 'a';
  class Client {
    constructor(options) { Object.assign(this, options); this.topics = []; clients.push(this); }
    activate() { this.active = true; this.beforeConnect(); }
    deactivate() { this.active = false; return new Promise((resolve) => { this.finishClose = resolve; }); }
    subscribe(topic) { this.topics.push(topic); return { unsubscribe() {} }; }
    connectedNow() { this.connected = true; this.onConnect(); }
  }
  const source = fs.readFileSync(path.join(__dirname, '../src/lib/ws.ts'), 'utf8');
  const context = {
    exports: {}, process: { env: {} },
    window: { addEventListener: (_, fn) => listeners.push(fn),
      location: { hostname: 'exchange.test', host: 'exchange.test', protocol: 'https:' } },
    require: (name) => name === '@stomp/stompjs' ? { Client } : {
      getAuthToken: () => token, getCurrentUser: () => ({ userId }),
    },
  };
  vm.runInNewContext(ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText, context);
  return { manager: context.exports.wsManager, clients,
    changeAuth(nextToken, nextUser) { token = nextToken; userId = nextUser; listeners.forEach((fn) => fn()); } };
}

test('auth change reconnects with current credentials and only the current private topic', async () => {
  const { manager, clients, changeAuth } = harness();
  manager.subscribe('/topic/orders.a', () => {});
  manager.subscribe('/topic/orders.b', () => {});
  manager.subscribe('/topic/trades.BTC_USD', () => {});
  manager.connect();
  const old = clients[0];
  old.connectedNow();
  assert.equal(old.connectHeaders.Authorization, 'Bearer token-a');
  assert.deepEqual(old.topics, ['/topic/orders.a', '/topic/trades.BTC_USD']);
  changeAuth('token-b', 'b');
  old.finishClose();
  await Promise.resolve();
  const current = clients[1];
  current.connectedNow();
  assert.equal(current.connectHeaders.Authorization, 'Bearer token-b');
  assert.deepEqual(current.topics, ['/topic/orders.b', '/topic/trades.BTC_USD']);
  old.onWebSocketClose();
  assert.equal(manager.getState(), 'connected');
});

test('disconnect during auth reconnection does not reopen a socket', async () => {
  const { manager, clients, changeAuth } = harness();
  manager.connect();
  changeAuth(null, null);
  manager.disconnect();
  clients[0].finishClose();
  await Promise.resolve();
  assert.equal(clients.length, 1);
  assert.equal(manager.getState(), 'disconnected');
});

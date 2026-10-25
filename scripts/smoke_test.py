#!/usr/bin/env python3
"""Demo end-to-end check. Creates two smoke accounts; retains their auditable ledger."""
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid

BASE_URL = os.environ.get('GATEWAY_URL', 'https://dete.kartik-mrk.me').rstrip('/')
SCALE = 100_000_000
QUANTITY = 500_000


def call(path, method='GET', data=None, token=None, expected=(200,), key=None, base=BASE_URL):
    headers = {'Content-Type': 'application/json', 'User-Agent': 'DETE-Smoke/1.0'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if key:
        headers['Idempotency-Key'] = key
    request = urllib.request.Request(base + path, method=method, headers=headers,
        data=json.dumps(data).encode() if data is not None else None)
    try:
        response = urllib.request.urlopen(request, timeout=20)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        raw = response.read()
        if response.status not in expected:
            raise RuntimeError(f'{method} {path}: HTTP {response.status}, expected {expected}')
        try:
            return json.loads(raw) if raw else None
        except json.JSONDecodeError:
            return raw.decode()


def eventually(check, label, timeout=40):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        value = check()
        if value:
            return value
        time.sleep(0.5)
    raise RuntimeError('Timed out: ' + label)


def new_account(role):
    username = 'smoke_' + role + '_' + uuid.uuid4().hex[:12]
    password = 'Smoke!' + uuid.uuid4().hex
    try:
        call('/auth/register', 'POST', {'username': username, 'email': username + '@example.invalid',
            'password': password}, expected=(201,))
    except Exception as registration_error:
        try:
            result = call('/auth/login', 'POST', {'identifier': username, 'password': password})
        except Exception:
            raise registration_error
    else:
        result = call('/auth/login', 'POST', {'identifier': username, 'password': password})
    return result['userId'], result['accessToken']


def balances(token):
    rows = call('/accounts/me/balances', token=token)
    if not isinstance(rows, list):
        raise RuntimeError('Invalid balance response')
    return {row['asset']: (row['available'], row['reserved']) for row in rows}


def verify_simulator_api(token):
    call('/market-simulation/status', expected=(401,))
    session = call('/market-simulation/status', token=token)
    if not isinstance(session, dict) or session.get('status') not in {
            'IDLE', 'ACTIVE', 'STOPPING', 'COMPLETED', 'FAILED'
    }:
        raise RuntimeError('Invalid authenticated market simulation status')


def select_price(book):
    bid = max((level['price'] for level in book['bids']), default=0)
    ask = min((level['price'] for level in book['asks']), default=0)
    if bid and ask:
        if ask - bid < 2:
            raise RuntimeError('No spread available for isolated smoke trade')
        return (bid + ask) // 2
    return bid + SCALE if bid else ask - SCALE if ask else 60_000 * SCALE


def verify_deltas(before_buyer, after_buyer, before_seller, after_seller, quantity, notional):
    expected = [('buyer USD', before_buyer, after_buyer, 'USD', -notional),
                ('buyer BTC', before_buyer, after_buyer, 'BTC', quantity),
                ('seller USD', before_seller, after_seller, 'USD', notional),
                ('seller BTC', before_seller, after_seller, 'BTC', -quantity)]
    for name, before, after, asset, delta in expected:
        available, reserved = before.get(asset, (0, 0))
        if after.get(asset, (0, 0)) != (available + delta, reserved):
            raise RuntimeError('Incorrect available/reserved delta: ' + name)


def run_smoke_test():
    created_orders = []
    try:
        page = call('/')
        if not isinstance(page, str) or '<html' not in page.lower():
            raise RuntimeError('Frontend did not serve HTML')
        if not call('/auth/.well-known/jwks.json').get('keys'):
            raise RuntimeError('JWKS is empty')
        print('PASS: frontend and auth reachable through ingress', flush=True)
        buyer, buyer_token = new_account('buyer')
        seller, seller_token = new_account('seller')
        verify_simulator_api(buyer_token)
        eventually(lambda: balances(buyer_token).get('USD', (0, 0))[0] >= 1_000 * SCALE, 'buyer funding')
        eventually(lambda: balances(seller_token).get('USD', (0, 0))[0] > 0, 'seller funding')
        account_url = os.environ.get('ACCOUNT_SERVICE_URL')
        if not account_url:
            ip = subprocess.check_output(['kubectl', 'get', 'service', 'account', '-n', 'dete',
                '-o', 'jsonpath={.spec.clusterIP}'], text=True).strip()
            account_url = 'http://' + ip + ':8082'
        # Only fixture funding uses the internal API. All exchange verification uses public ingress.
        call('/internal/accounts/deposit', 'POST', {'accountId': seller, 'asset': 'BTC',
            'amount': QUANTITY, 'referenceId': str(uuid.uuid4())}, base=account_url)
        call('/accounts/' + seller + '/balances', token=buyer_token, expected=(403,))
        call('/accounts/me/balances', expected=(401, 403))
        print('PASS: new demo accounts funded; cross-account and anonymous access denied', flush=True)
        before_buyer, before_seller = balances(buyer_token), balances(seller_token)
        price = select_price(call('/market-data/BTC_USD/orderbook'))
        def place(side, token):
            order = call('/orders', 'POST', {'instrument': 'BTC_USD', 'side': side,
                'orderType': 'LIMIT', 'price': price, 'quantity': QUANTITY}, token=token,
                key=str(uuid.uuid4()), expected=(202,))
            created_orders.append((order['orderId'], token))
            return order['orderId']
        sell_id = place('SELL', seller_token)
        eventually(lambda: call('/orders/' + sell_id, token=seller_token).get('status') == 'ACCEPTED', 'resting sell')
        buy_id = place('BUY', buyer_token)
        for order_id, token in [(sell_id, seller_token), (buy_id, buyer_token)]:
            eventually(lambda: call('/orders/' + order_id, token=token).get('status') == 'FILLED', 'both orders filled')
        def settled_trades():
            rows = call('/accounts/me/trades?limit=100', token=buyer_token)
            ours = [row for row in rows if row.get('buyerId') == buyer and row.get('sellerId') == seller]
            return ours if sum(row['quantity'] for row in ours) == QUANTITY else None
        settled = eventually(settled_trades, 'buyer/seller settlement')
        ids = {row['tradeId'] for row in settled}
        def tape_visible():
            rows = call('/market-data/BTC_USD/trades?limit=500')
            if any(set(row) & {'buyAccountId', 'sellAccountId', 'buyOrderId', 'sellOrderId'} for row in rows):
                raise RuntimeError('Public tape exposes private identifiers')
            return ids.issubset({row['tradeId'] for row in rows})
        eventually(tape_visible, 'settled trades visible on public tape')
        notional = sum(row['price'] * row['quantity'] // SCALE for row in settled)
        verify_deltas(before_buyer, balances(buyer_token), before_seller, balances(seller_token), QUANTITY, notional)
        print('PASS: fills, shared settlement/tape IDs, exact balance deltas and released reservations', flush=True)
        print('SMOKE PASSED: ingress -> auth -> risk -> reserve -> match -> settle -> market data', flush=True)
        return True
    except Exception as error:
        print('SMOKE FAILED: ' + str(error), flush=True)
        return False
    finally:
        for order_id, token in created_orders:
            try:
                order = call('/orders/' + order_id, token=token)
                if order.get('status') in ('SUBMITTED', 'ACCEPTED', 'PARTIALLY_FILLED'):
                    call('/orders/' + order_id, 'DELETE', token=token, expected=(200, 202))
            except Exception:
                print('Cleanup needs inspection for smoke order ' + order_id, flush=True)
if __name__ == '__main__':
    sys.exit(0 if run_smoke_test() else 1)

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


class HTTPStatusError(RuntimeError):
    def __init__(self, method, path, status, expected):
        super().__init__(f'{method} {path}: HTTP {status}, expected {expected}')
        self.status = status


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
            raise HTTPStatusError(method, path, response.status, expected)
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
            'IDLE', 'STARTING', 'ACTIVE', 'STOPPING', 'COMPLETED', 'FAILED', 'MAINTENANCE'
    }:
        raise RuntimeError('Invalid authenticated market simulation status')
    return session


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


def verify_execution_delta(before,after,trades,side):
    quantity=sum(t['quantity'] for t in trades)
    notional=sum(t['price']*t['quantity']//SCALE for t in trades)
    if quantity<=0: raise RuntimeError('No execution to verify')
    sign=1 if side=='BUY' else -1
    for asset,delta in [('BTC',sign*quantity),('USD',-sign*notional)]:
        available,reserved=before.get(asset,(0,0))
        if after.get(asset,(0,0))!=(available+delta,reserved): raise RuntimeError('Incorrect execution balance or reservation delta: '+asset)

def start_smoke_session(token):
    def attempt():
        status=call('/market-simulation/status',token=token)['status']
        if status not in ('IDLE','COMPLETED','FAILED','STOPPING'): return None
        try:
            return call('/market-simulation/start','POST',{'durationSeconds':60},token=token,expected=(202,))
        except HTTPStatusError as error:
            if error.status!=409: raise
            return None  # Another session or its worker still owns cleanup; retry after polling.
    return eventually(attempt,'start or recover smoke session',timeout=720)


def run_smoke_test():
    created_orders=[]
    try:
        page=call('/')
        if not isinstance(page,str) or '<html' not in page.lower(): raise RuntimeError('Frontend did not serve HTML')
        if not call('/auth/.well-known/jwks.json').get('keys'): raise RuntimeError('JWKS is empty')
        buyer,buyer_token=new_account('buyer')
        session=verify_simulator_api(buyer_token)
        if session['status']=='MAINTENANCE':
            clock=session.get('clock',{})
            if not clock.get('epochId') or clock.get('tradingAllowed'): raise RuntimeError('Invalid maintenance clock')
            call('/orders','POST',{'instrument':'BTC_USD','side':'BUY','orderType':'LIMIT','price':60000*SCALE,'quantity':QUANTITY},token=buyer_token,key=str(uuid.uuid4()),expected=(409,))
            print('READINESS PASSED: ingress, authentication, initialized maintenance clock and closed trading gate. Trading validation remains deferred to the controlled first run.',flush=True)
            return True
        seller,seller_token=new_account('seller')
        eventually(lambda:balances(buyer_token).get('USD',(0,0))[0]>=1000*SCALE,'buyer funding')
        account_url=os.environ.get('ACCOUNT_SERVICE_URL')
        if not account_url:
            ip=subprocess.check_output(['kubectl','get','svc','account','-n',os.environ.get('DETE_NAMESPACE','dete'),'-o','jsonpath={.spec.clusterIP}'],text=True).strip()
            account_url='http://'+ip+':8082'
        call('/internal/accounts/deposit','POST',{'accountId':seller,'asset':'BTC','amount':SCALE,'referenceId':str(uuid.uuid4())},base=account_url)
        started=start_smoke_session(buyer_token)
        session_id=started['clock']['sessionId']
        before_buyer=balances(buyer_token);before_seller=balances(seller_token)
        def liquid_book():
            book=call('/market-data/BTC_USD/orderbook')
            return book if book['bids'] and book['asks'] else None
        eventually(liquid_book,'two-sided bot liquidity')
        def place(side,token):
            book=liquid_book()
            if not book: raise RuntimeError('No liquidity for smoke trade')
            price=min(l['price'] for l in book['asks']) if side=='BUY' else max(l['price'] for l in book['bids'])
            order=call('/orders','POST',{'instrument':'BTC_USD','side':side,'orderType':'IOC','price':price,'quantity':QUANTITY,'sessionId':session_id},token=token,key=str(uuid.uuid4()),expected=(202,))
            created_orders.append((order['orderId'],token))
            return order['orderId']
        buy_id=place('BUY',buyer_token);sell_id=place('SELL',seller_token)
        for order_id,token in [(buy_id,buyer_token),(sell_id,seller_token)]:
            eventually(lambda:call('/orders/'+order_id,token=token).get('status') in ('FILLED','CANCELLED'),'IOC terminal status')
        def executions(token):
            rows=call('/accounts/me/trades?limit=100',token=token)
            return rows or None
        buy_trades=eventually(lambda:executions(buyer_token),'buyer settlement')
        sell_trades=eventually(lambda:executions(seller_token),'seller settlement')
        ids={t['tradeId'] for t in buy_trades+sell_trades}
        def tape_visible():
            rows=call('/market-data/BTC_USD/trades?limit=500')
            if any(set(row)&{'buyAccountId','sellAccountId','buyOrderId','sellOrderId'} for row in rows): raise RuntimeError('Public tape exposes private identifiers')
            return ids.issubset({row['tradeId'] for row in rows})
        eventually(tape_visible,'settled trades on tape')
        completed=eventually(lambda:(c if (c:=call('/market-simulation/status',token=buyer_token))['status']=='COMPLETED' else None),'session finalization',timeout=150)
        if completed['sessionId']!=session_id: raise RuntimeError('Unexpected session replaced smoke session')
        verify_execution_delta(before_buyer,balances(buyer_token),buy_trades,'BUY')
        verify_execution_delta(before_seller,balances(seller_token),sell_trades,'SELL')
        call('/orders','POST',{'instrument':'BTC_USD','side':'BUY','orderType':'LIMIT','price':60000*SCALE,'quantity':QUANTITY},token=buyer_token,key=str(uuid.uuid4()),expected=(409,))
        print('SMOKE PASSED: active-session admission, actual fills/settlement/tape, exact balances, released reservations and paused rejection',flush=True)
        return True
    except Exception as error:
        print('SMOKE FAILED: '+str(error),flush=True)
        return False
    finally:
        for order_id,token in created_orders:
            try:
                order=call('/orders/'+order_id,token=token)
                if order.get('status') in ('SUBMITTED','ACCEPTED','PARTIALLY_FILLED'): call('/orders/'+order_id,'DELETE',token=token,expected=(200,202))
            except Exception: print('Cleanup needs inspection for smoke order '+order_id,flush=True)

if __name__=='__main__': sys.exit(0 if run_smoke_test() else 1)

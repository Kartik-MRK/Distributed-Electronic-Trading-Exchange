#!/usr/bin/env python3
"""Five active minutes, a frozen minute, then another user's minute; compare exact executions.
Writes evidence without passwords or bearer tokens. Requires a fresh, enabled epoch.
"""
import argparse
import collections
import datetime
import json
import os
from pathlib import Path
import subprocess
import time
import uuid
from smoke_test import call, eventually

MARKETS=('BTC_USD','ETH_USD','SOL_USD')

def expected_candles(trades,t0,interval):
    grouped=collections.defaultdict(list)
    for t in trades: grouped[t['elapsed_ms']//interval].append(t)
    result=[]
    for window,rows in sorted(grouped.items()):
        rows.sort(key=lambda t:(t['elapsed_ms'],t['sequence_number']))
        prices=[t['price'] for t in rows]
        result.append(dict(openTime=t0+window*interval,open=prices[0],high=max(prices),low=min(prices),close=prices[-1],volume=sum(t['quantity'] for t in rows),tradeCount=len(rows)))
    return result

def compare_candles(expected,actual):
    fields=('openTime','open','high','low','close','volume','tradeCount')
    assert [{k:c[k] for k in fields} for c in actual]==expected, 'Candle OHLCV differs from executions'

def main():
    os.umask(0o077)
    parser=argparse.ArgumentParser()
    parser.add_argument('--base',required=True)
    parser.add_argument('--namespace',default='dete')
    parser.add_argument('--compose',type=Path)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    def request(path,**kwargs): return call(path,base=args.base,**kwargs)
    def sql(query):
        prefix=['docker','compose','-p','dete-clock-preview','-f',str(args.compose),'exec','-T','postgres'] if args.compose else ['kubectl','exec','-i','-n',args.namespace,'postgres-0','--']
        return subprocess.check_output(prefix+['psql','-X','-qAt','-v','ON_ERROR_STOP=1','-U','dete','-d','dete'],input=query.encode()).decode().strip()
    def account(role):
        name='clock_'+role+'_'+uuid.uuid4().hex[:8]; password='Clock!'+uuid.uuid4().hex
        request('/auth/register',method='POST',data=dict(username=name,email=name+'@example.invalid',password=password),expected=(201,))
        auth=request('/auth/login',method='POST',data=dict(identifier=name,password=password))
        return auth['userId'],auth['accessToken']
    first,token=account('first'); second,other=account('second')
    eventually(lambda:any(b['asset']=='USD' and b['available']>=100000000000 for b in request('/accounts/me/balances',token=other)),'second account welcome funding')
    def clock(): return request('/market-simulation/status',token=token)['clock']
    initial=clock(); assert initial['elapsedMs']==0 and initial['status']=='IDLE', initial
    epoch=str(uuid.UUID(initial['epochId']))
    t0=round(datetime.datetime.fromisoformat(initial['initializedAt'].replace('Z','+00:00')).timestamp()*1000)
    report={'epoch':epoch,'initializedAt':initial['initializedAt'],'startedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'snapshots':[]}
    args.output.parent.mkdir(parents=True,exist_ok=True)
    def save(): args.output.write_text(json.dumps(report,indent=2))
    def paused_checks():
        before=request('/accounts/me/balances',token=other)
        response=request('/orders',method='POST',data=dict(instrument='BTC_USD',side='BUY',orderType='LIMIT',price=60000*100000000,quantity=10000),token=other,key=str(uuid.uuid4()),expected=(409,))
        assert response.get('code')=='SIMULATION_NOT_ACTIVE',response
        request('/accounts/deposit',method='POST',data=dict(accountId=second,asset='USD',amount=100,referenceId=str(uuid.uuid4())),token=other,expected=(403,409))
        assert before==request('/accounts/me/balances',token=other), 'Paused mutation changed balances'
    def run(seconds,owner):
        started=request('/market-simulation/start',method='POST',data={'durationSeconds':seconds},token=owner,expected=(202,))
        assert started['clock']['tradingAllowed']
        session=started['clock']['sessionId']
        print('Started '+str(seconds)+'s session '+session,flush=True)
        # A second authenticated user can submit in the shared session, and cancel while active.
        order=request('/orders',method='POST',data=dict(instrument='BTC_USD',side='BUY',orderType='LIMIT',price=60000*100000000,quantity=10000,sessionId=session),token=other,key=str(uuid.uuid4()),expected=(202,))
        request('/orders/'+order['orderId'],method='DELETE',token=other,expected=(200,202,400,409))
        deadline=time.monotonic()+seconds+100
        while time.monotonic()<deadline:
            current=clock(); report['snapshots'].append(current); save()
            if current['status']=='COMPLETED': return current
            if current['status']=='FAILED': raise AssertionError(current)
            time.sleep(2)
        raise AssertionError('Session did not complete; inspect STOPPING drain')
    paused_checks()
    complete=run(300,token)
    assert complete['elapsedMs']==300000,complete
    def verify(minutes):
        all_trades=json.loads(sql("SELECT coalesce(json_agg(t),'[]'::json) FROM (SELECT instrument,elapsed_ms,sequence_number,price,quantity FROM market_data.trades WHERE epoch_id='"+epoch+"') t;"))
        evidence={}
        for market in MARKETS:
            trades=[t for t in all_trades if t['instrument']==market]
            minute=expected_candles(trades,t0,60000)
            assert len(minute)==minutes and [c['openTime'] for c in minute]==[t0+i*60000 for i in range(minutes)], 'Missing trade minute for '+market
            actual=request('/market-data/'+market+'/candles?interval=1m&limit=1000&epoch='+epoch)
            compare_candles(minute,actual)
            compare_candles(expected_candles(trades,t0,300000),request('/market-data/'+market+'/candles?interval=5m&limit=1000&epoch='+epoch))
            evidence[market]={'trades':len(trades),'candles':actual,'price':request('/market-data/'+market+'/price')}
        assert sql('SELECT count(*) FROM account.balances WHERE reserved<>0;')=='0', 'Reserved balances remain'
        assert sql('SELECT count(*) FROM account.order_reservations WHERE NOT closed;')=='0', 'Reservations remain open'
        assert sql('SELECT count(*) FROM simulator.order_intent WHERE NOT closed;')=='0', 'Intents remain open'
        mismatch=sql("WITH l AS (SELECT account_id,asset,sum(CASE WHEN entry_type IN ('DEPOSIT','CREDIT') THEN amount WHEN entry_type IN ('WITHDRAWAL','DEBIT','FEE') THEN -amount ELSE 0 END) total FROM account.ledger_entries GROUP BY account_id,asset) SELECT count(*) FROM account.balances b FULL JOIN l USING(account_id,asset) WHERE coalesce(b.available::numeric+b.reserved,0)<>coalesce(l.total,0);")
        assert mismatch=='0','Ledger conservation failed'
        return evidence
    report['fiveMinutes']=verify(5); save()
    print('Verified exact five-minute OHLCV and ledger; checking 60s frozen interval',flush=True)
    paused_checks(); time.sleep(60)
    assert clock()['elapsedMs']==300000
    assert verify(5)==report['fiveMinutes'],'Frozen data changed'
    report['frozenForSeconds']=60; save()
    resumed=run(60,other)
    assert resumed['elapsedMs']==360000,resumed
    report['sixMinutes']=verify(6); report['completedAt']=datetime.datetime.now(datetime.timezone.utc).isoformat();save()
    print('PASS: five minutes, frozen minute, second-user resume, exact candles and reconciled balances',flush=True)

if __name__=='__main__': main()

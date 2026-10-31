/* eslint-disable @typescript-eslint/no-require-imports */
const {test}=require('node:test');
const assert=require('node:assert/strict');
const ts=require('typescript');
const fs=require('node:fs');
const Module=require('node:module');
const source=fs.readFileSync(require('node:path').join(__dirname,'../src/lib/simulation-clock.ts'),'utf8');
const compiled=new Module('simulation-clock');
compiled._compile(ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText,'simulation-clock.js');
const {projectClock,newerSnapshot,mergeCandles}=compiled.exports;
const snapshot={epochId:'epoch',initializedAt:'2026-10-30T12:34:27.123Z',serverNow:'2026-10-30T14:00:00Z',sessionId:'one',status:'ACTIVE',fence:1,elapsedMs:59900,remainingMs:100,leaseUntil:'2026-10-30T14:00:02Z',expiresAt:'2026-10-30T14:00:00.100Z',tradingAllowed:true};
test('deadline freezes clock and disables trading before another poll',()=>{
  assert.equal(projectClock(snapshot,1000,1050,true).elapsedMs,59950);
  const ended=projectClock(snapshot,1000,2000,true);
  assert.equal(ended.elapsedMs,60000);assert.equal(ended.remainingMs,0);assert.equal(ended.tradingAllowed,false);
});
test('paused days and failed or stale status cannot open trading',()=>{
  const paused={...snapshot,status:'COMPLETED',elapsedMs:300000,remainingMs:0,tradingAllowed:false};
  assert.equal(projectClock(paused,0,172800000,true).elapsedMs,300000);
  assert.equal(projectClock(snapshot,1000,1050,false).tradingAllowed,false);
  assert.equal(projectClock({...snapshot,remainingMs:300000,expiresAt:'2026-10-30T14:05:00Z'},0,3000,true).tradingAllowed,false);
});
test('two viewers use server time independently of browser wall clock and stale responses',()=>{
  assert.deepEqual(projectClock(snapshot,1000,1050,true),projectClock(snapshot,90000,90050,true));
  assert.equal(newerSnapshot(snapshot,{...snapshot,serverNow:'2026-10-30T13:59:59Z'}),false);
  assert.equal(newerSnapshot(snapshot,{...snapshot,serverNow:'2026-10-30T14:00:01Z'}),true);
});
const candle=(time,revision,close=100)=>({epochId:'epoch',openTime:time,revision,open:100,high:110,low:90,close,volume:1});
test('history and live revisions merge in order without losing corrections',()=>{
  const merged=mergeCandles([candle(2000,3)],[candle(1000,1),candle(2000,2)],'epoch');
  assert.deepEqual(merged.map(c=>c.revision),[1,3]);
  assert.equal(mergeCandles(merged,[candle(1000,4,105)],'epoch')[0].close,105);
  assert.equal(mergeCandles(merged,[{...candle(3000,9),epochId:'old'}],'epoch').length,2);
  assert.throws(()=>mergeCandles([], [{...candle(1000,1),openTime:undefined}],'epoch'));
});

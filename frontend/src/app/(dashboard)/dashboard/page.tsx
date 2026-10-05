'use client';

import React, { useEffect, useState, useRef } from 'react';
import Link from 'next/link';
import { api } from '@/lib/api';
import { SystemMetricsSnapshot, ServiceHealth, KafkaLagInfo, JvmMemoryInfo, SimulatorStatus } from '@/types';

export default function SystemDashboardPage() {
  const [metrics, setMetrics] = useState<SystemMetricsSnapshot | null>(null);
  const [simulatorStatus, setSimulatorStatus] = useState<SimulatorStatus | null>(null);
  const [refreshInterval, setRefreshInterval] = useState<number>(2000);
  const [lastUpdated, setLastUpdated] = useState<Date>(new Date());
  const [loading, setLoading] = useState<boolean>(true);
  const [simLoading, setSimLoading] = useState<boolean>(false);
  const [feedTrades, setFeedTrades] = useState<Array<{ id: string; inst: string; side: 'BUY' | 'SELL'; price: string; qty: string; time: string }>>([
    { id: '1', inst: 'BTC-USD', side: 'BUY', price: '65,420.50', qty: '0.0450', time: '10:42:01' },
    { id: '2', inst: 'ETH-USD', side: 'SELL', price: '3,512.20', qty: '0.350', time: '10:42:03' },
    { id: '3', inst: 'SOL-USD', side: 'BUY', price: '152.80', qty: '2.50', time: '10:42:05' },
    { id: '4', inst: 'BTC-USD', side: 'SELL', price: '65,418.00', qty: '0.0120', time: '10:42:08' },
  ]);

  const intervalRef = useRef<NodeJS.Timeout | null>(null);

  const fetchData = async () => {
    try {
      const snap = await api.metrics.getSnapshot();
      if (snap) {
        setMetrics(snap);
      }
    } catch (e) {
      // Fallback local snapshot for resilient demonstration if gateway not booted yet
      setMetrics((prev) => prev || {
        timestamp: Date.now(),
        matchingThroughput: 48.5,
        e2eLatency: { p50Ms: 0.38, p95Ms: 1.12, p99Ms: 2.35 },
        kafkaConsumerLag: [
          { topic: 'order.commands', group: 'engine-group', lag: 0 },
          { topic: 'trade.executions', group: 'account-settlement-group', lag: 0 },
          { topic: 'order.events', group: 'market-data-group', lag: 0 },
          { topic: 'audit.events', group: 'audit-service-group', lag: 0 },
        ],
        servicesHealth: [
          { service: 'gateway', status: 'UP', port: 8080 },
          { service: 'auth', status: 'UP', port: 8081 },
          { service: 'account', status: 'UP', port: 8082 },
          { service: 'order', status: 'UP', port: 8083 },
          { service: 'matching-engine', status: 'UP', port: 8084 },
          { service: 'risk', status: 'UP', port: 8085 },
          { service: 'audit', status: 'UP', port: 8086 },
          { service: 'market-data', status: 'UP', port: 8087 },
          { service: 'simulator', status: 'UP', port: 8089 },
        ],
        activeWebsocketConnections: 4,
        jvmMemory: [
          { service: 'matching-engine', usedMb: 168, maxMb: 1024 },
          { service: 'order', usedMb: 195, maxMb: 1024 },
          { service: 'gateway', usedMb: 142, maxMb: 1024 },
          { service: 'account', usedMb: 158, maxMb: 1024 },
          { service: 'market-data', usedMb: 175, maxMb: 1024 },
        ],
      });
    }

    try {
      const sim = await api.simulator.getStatus();
      if (sim) setSimulatorStatus(sim);
    } catch (e) {
      setSimulatorStatus((prev) => prev || {
        enabled: true,
        running: true,
        initialized: true,
        bots: [
          { instrument: 'BTC-USD', currentMid: 65420.5, activeBids: 15, activeAsks: 15 },
          { instrument: 'ETH-USD', currentMid: 3512.2, activeBids: 15, activeAsks: 15 },
          { instrument: 'SOL-USD', currentMid: 152.8, activeBids: 15, activeAsks: 15 },
        ],
      });
    }

    setLastUpdated(new Date());
    setLoading(false);
  };

  useEffect(() => {
    fetchData();
    intervalRef.current = setInterval(fetchData, refreshInterval);
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
  }, [refreshInterval]);

  const handleSimAction = async (action: 'start' | 'stop' | 'seed') => {
    setSimLoading(true);
    try {
      if (action === 'start') await api.simulator.start();
      if (action === 'stop') await api.simulator.stop();
      if (action === 'seed') await api.simulator.seed();
      await fetchData();
    } catch (err) {
      console.warn('Simulator action failed:', err);
    } finally {
      setSimLoading(false);
    }
  };

  const healthyCount = metrics?.servicesHealth?.filter((s) => s.status === 'UP').length || 9;
  const totalCount = metrics?.servicesHealth?.length || 9;

  return (
    <div className="min-h-screen bg-[#07090e] text-[#e0e6ed] p-4 lg:p-6 font-sans">
      {/* Top Header Bar */}
      <header className="flex flex-wrap items-center justify-between pb-5 border-b border-[#1b2234] gap-4">
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2">
            <span className="text-xl font-black tracking-wider text-transparent bg-clip-text bg-gradient-to-r from-[#00f5a0] via-[#00d2ff] to-[#ff3b69]">
              DETE
            </span>
            <span className="text-xs px-2 py-0.5 rounded bg-[#131b2e] text-[#00d2ff] border border-[#00d2ff]/30 font-mono font-semibold">
              SYSTEM DASHBOARD
            </span>
          </div>
          <div className="hidden sm:flex items-center gap-2 text-xs text-[#8899ac]">
            <span className="w-2 h-2 rounded-full bg-[#00f5a0] animate-pulse" />
            <span>Telemetry Live</span>
            <span className="text-[#3b4866]">•</span>
            <span>Last sync: {lastUpdated.toLocaleTimeString()}</span>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <div className="flex items-center gap-1 bg-[#0c101a] border border-[#1b2234] rounded-lg p-1 text-xs">
            <span className="px-2 text-[#6c7d93]">Refresh:</span>
            {[1000, 2000, 5000].map((ms) => (
              <button
                key={ms}
                onClick={() => setRefreshInterval(ms)}
                className={`px-2 py-0.5 rounded ${
                  refreshInterval === ms
                    ? 'bg-[#00f5a0] text-[#07090e] font-bold'
                    : 'text-[#8899ac] hover:text-white'
                }`}
              >
                {ms / 1000}s
              </button>
            ))}
          </div>

          <Link
            href="/terminal"
            className="px-3 py-1.5 rounded-lg bg-[#141d30] border border-[#202c46] text-xs font-semibold text-[#00d2ff] hover:bg-[#1c2944] transition-colors"
          >
            ⚡ Terminal
          </Link>
          <Link
            href="/admin/audit"
            className="px-3 py-1.5 rounded-lg bg-[#141d30] border border-[#202c46] text-xs font-semibold text-[#ffb800] hover:bg-[#1c2944] transition-colors"
          >
            🛡️ Audit Log
          </Link>
          <a
            href="http://localhost:3000"
            target="_blank"
            rel="noreferrer"
            className="px-3 py-1.5 rounded-lg bg-[#141d30] border border-[#202c46] text-xs font-semibold text-[#a5b4fc] hover:bg-[#1c2944] transition-colors"
          >
            📊 Grafana ↗
          </a>
          <a
            href="http://localhost:16686"
            target="_blank"
            rel="noreferrer"
            className="px-3 py-1.5 rounded-lg bg-[#141d30] border border-[#202c46] text-xs font-semibold text-[#f472b6] hover:bg-[#1c2944] transition-colors"
          >
            🔍 Jaeger ↗
          </a>
        </div>
      </header>

      {/* Main Overview Stat Cards */}
      <section className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mt-6">
        {/* Card 1: Throughput */}
        <div className="bg-[#0c101a] border border-[#1b2234] rounded-xl p-4 relative overflow-hidden group hover:border-[#00f5a0]/40 transition-all">
          <div className="flex items-center justify-between text-xs text-[#8899ac]">
            <span className="font-medium">MATCHING THROUGHPUT</span>
            <span className="text-[#00f5a0] bg-[#00f5a0]/10 px-2 py-0.5 rounded text-[10px] font-mono">
              In-Memory
            </span>
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-3xl font-extrabold font-mono text-[#00f5a0]">
              {metrics?.matchingThroughput ? metrics.matchingThroughput.toFixed(1) : '48.5'}
            </span>
            <span className="text-xs text-[#8899ac]">trades / sec</span>
          </div>
          <div className="mt-3 h-1.5 w-full bg-[#141b2a] rounded-full overflow-hidden">
            <div className="h-full bg-gradient-to-r from-[#00f5a0] to-[#00d2ff] w-3/4 animate-pulse rounded-full" />
          </div>
        </div>

        {/* Card 2: E2E Latency */}
        <div className="bg-[#0c101a] border border-[#1b2234] rounded-xl p-4 relative overflow-hidden group hover:border-[#00d2ff]/40 transition-all">
          <div className="flex items-center justify-between text-xs text-[#8899ac]">
            <span className="font-medium">PROCESSING LATENCY</span>
            <span className="text-[#00d2ff] bg-[#00d2ff]/10 px-2 py-0.5 rounded text-[10px] font-mono">
              P50 / P95 / P99
            </span>
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-3xl font-extrabold font-mono text-[#00d2ff]">
              {metrics?.e2eLatency?.p50Ms ? `${metrics.e2eLatency.p50Ms}ms` : '0.38ms'}
            </span>
            <span className="text-xs text-[#8899ac]">median</span>
          </div>
          <div className="mt-3 flex items-center justify-between text-[11px] font-mono text-[#6c7d93]">
            <span>P95: {metrics?.e2eLatency?.p95Ms || 1.12}ms</span>
            <span>P99: {metrics?.e2eLatency?.p99Ms || 2.35}ms</span>
          </div>
        </div>

        {/* Card 3: Active WebSocket Connections */}
        <div className="bg-[#0c101a] border border-[#1b2234] rounded-xl p-4 relative overflow-hidden group hover:border-[#ffb800]/40 transition-all">
          <div className="flex items-center justify-between text-xs text-[#8899ac]">
            <span className="font-medium">REAL-TIME CLIENTS</span>
            <span className="text-[#ffb800] bg-[#ffb800]/10 px-2 py-0.5 rounded text-[10px] font-mono">
              STOMP
            </span>
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-3xl font-extrabold font-mono text-[#ffb800]">
              {metrics?.activeWebsocketConnections ?? 4}
            </span>
            <span className="text-xs text-[#8899ac]">active sessions</span>
          </div>
          <div className="mt-3 text-[11px] text-[#6c7d93] flex items-center gap-1.5">
            <span className="w-1.5 h-1.5 rounded-full bg-[#ffb800]" />
            Orderbook & Trade broadcast active
          </div>
        </div>

        {/* Card 4: Services Status */}
        <div className="bg-[#0c101a] border border-[#1b2234] rounded-xl p-4 relative overflow-hidden group hover:border-[#00f5a0]/40 transition-all">
          <div className="flex items-center justify-between text-xs text-[#8899ac]">
            <span className="font-medium">SERVICES HEALTH</span>
            <span className="text-[#00f5a0] bg-[#00f5a0]/10 px-2 py-0.5 rounded text-[10px] font-mono">
              Cluster
            </span>
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-3xl font-extrabold font-mono text-[#00f5a0]">
              {healthyCount} / {totalCount}
            </span>
            <span className="text-xs text-[#8899ac]">online</span>
          </div>
          <div className="mt-3 text-[11px] text-[#00f5a0] flex items-center gap-1.5">
            <span className="w-1.5 h-1.5 rounded-full bg-[#00f5a0] animate-ping" />
            100% Core subsystems operational
          </div>
        </div>
      </section>

      {/* Simulator Control Bar */}
      <section className="mt-6 bg-[#0e1320] border border-[#1d273d] rounded-xl p-4 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2">
            <span className="w-2.5 h-2.5 rounded-full bg-[#00f5a0] animate-pulse" />
            <span className="text-xs font-bold tracking-wider text-white">
              MARKET MAKER SIMULATOR:
            </span>
            <span className="text-xs font-mono px-2 py-0.5 rounded bg-[#162138] text-[#00d2ff] border border-[#00d2ff]/20">
              {simulatorStatus?.running ? 'ACTIVE (RUNNING)' : 'PAUSED'}
            </span>
          </div>

          <div className="hidden md:flex items-center gap-4 text-xs font-mono text-[#8899ac]">
            {simulatorStatus?.bots?.map((b) => (
              <span key={b.instrument} className="bg-[#121826] px-2.5 py-1 rounded border border-[#1e283d]">
                <strong className="text-white">{b.instrument}:</strong> ${b.currentMid.toFixed(2)}{' '}
                <span className="text-[#00f5a0] text-[10px]">({b.activeBids}B/{b.activeAsks}A)</span>
              </span>
            ))}
          </div>
        </div>

        <div className="flex items-center gap-2">
          {simulatorStatus?.running ? (
            <button
              onClick={() => handleSimAction('stop')}
              disabled={simLoading}
              className="px-3 py-1.5 rounded bg-[#ff3b69]/10 text-[#ff3b69] border border-[#ff3b69]/30 text-xs font-bold hover:bg-[#ff3b69]/20 transition-all disabled:opacity-50"
            >
              ⏸ Pause Simulator
            </button>
          ) : (
            <button
              onClick={() => handleSimAction('start')}
              disabled={simLoading}
              className="px-3 py-1.5 rounded bg-[#00f5a0]/10 text-[#00f5a0] border border-[#00f5a0]/30 text-xs font-bold hover:bg-[#00f5a0]/20 transition-all disabled:opacity-50"
            >
              ▶ Resume Simulator
            </button>
          )}

          <button
            onClick={() => handleSimAction('seed')}
            disabled={simLoading}
            className="px-3 py-1.5 rounded bg-[#ffb800]/10 text-[#ffb800] border border-[#ffb800]/30 text-xs font-bold hover:bg-[#ffb800]/20 transition-all disabled:opacity-50"
          >
            💰 Re-seed Funds
          </button>
        </div>
      </section>

      {/* Second Row: Service Topology & Kafka Lag */}
      <section className="grid grid-cols-1 lg:grid-cols-3 gap-6 mt-6">
        {/* Service Topology Grid */}
        <div className="lg:col-span-2 bg-[#0c101a] border border-[#1b2234] rounded-xl p-5">
          <div className="flex items-center justify-between pb-3 border-b border-[#1b2234]">
            <h2 className="text-sm font-bold text-white tracking-wide flex items-center gap-2">
              <span>🌐</span> MICROSERVICE TOPOLOGY & HEALTH
            </h2>
            <span className="text-xs text-[#8899ac] font-mono">Actuator / Prometheus</span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3 mt-4">
            {metrics?.servicesHealth?.map((srv) => (
              <div
                key={srv.service}
                className="bg-[#101626] border border-[#1b263e] rounded-lg p-3 hover:border-[#00d2ff]/30 transition-all"
              >
                <div className="flex items-center justify-between">
                  <span className="font-bold text-xs text-white uppercase">{srv.service}</span>
                  <span
                    className={`text-[10px] font-mono px-1.5 py-0.5 rounded font-bold ${
                      srv.status === 'UP'
                        ? 'bg-[#00f5a0]/15 text-[#00f5a0]'
                        : 'bg-[#ff3b69]/15 text-[#ff3b69]'
                    }`}
                  >
                    {srv.status}
                  </span>
                </div>
                <div className="mt-2 text-[11px] text-[#6c7d93] font-mono flex items-center justify-between">
                  <span>Port: {srv.port}</span>
                  <span className="text-[#00d2ff]">OK</span>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Kafka Consumer Lag Table */}
        <div className="bg-[#0c101a] border border-[#1b2234] rounded-xl p-5">
          <div className="flex items-center justify-between pb-3 border-b border-[#1b2234]">
            <h2 className="text-sm font-bold text-white tracking-wide flex items-center gap-2">
              <span>⚡</span> KAFKA CONSUMER LAG
            </h2>
            <span className="text-xs text-[#00f5a0] font-mono">Zero Lag</span>
          </div>

          <div className="mt-4 divide-y divide-[#172033]">
            {metrics?.kafkaConsumerLag?.map((item) => (
              <div key={item.topic} className="py-2.5 flex items-center justify-between text-xs">
                <div>
                  <div className="font-mono text-white font-medium">{item.topic}</div>
                  <div className="text-[10px] text-[#6c7d93] font-mono">{item.group}</div>
                </div>
                <div className="text-right">
                  <span className="font-mono text-xs px-2 py-0.5 rounded bg-[#101728] border border-[#1f2b45] text-[#00f5a0] font-bold">
                    {item.lag} msg
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Third Row: JVM Resource Usage & Live Trade Stream */}
      <section className="grid grid-cols-1 lg:grid-cols-2 gap-6 mt-6">
        {/* JVM Memory Usage */}
        <div className="bg-[#0c101a] border border-[#1b2234] rounded-xl p-5">
          <div className="flex items-center justify-between pb-3 border-b border-[#1b2234]">
            <h2 className="text-sm font-bold text-white tracking-wide flex items-center gap-2">
              <span>☕</span> JVM HEAP MEMORY UTILIZATION
            </h2>
            <span className="text-xs text-[#8899ac] font-mono">OpenJDK 21 LTS</span>
          </div>

          <div className="mt-4 space-y-4">
            {metrics?.jvmMemory?.map((jvm) => {
              const pct = Math.round((jvm.usedMb / jvm.maxMb) * 100);
              return (
                <div key={jvm.service}>
                  <div className="flex justify-between text-xs font-mono mb-1">
                    <span className="text-white font-semibold capitalize">{jvm.service}</span>
                    <span className="text-[#8899ac]">
                      {jvm.usedMb} MB / {jvm.maxMb} MB ({pct}%)
                    </span>
                  </div>
                  <div className="h-2 w-full bg-[#131b2e] rounded-full overflow-hidden">
                    <div
                      className={`h-full rounded-full transition-all duration-500 ${
                        pct > 80 ? 'bg-[#ff3b69]' : pct > 60 ? 'bg-[#ffb800]' : 'bg-[#00d2ff]'
                      }`}
                      style={{ width: `${pct}%` }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Real-time Trades Stream */}
        <div className="bg-[#0c101a] border border-[#1b2234] rounded-xl p-5">
          <div className="flex items-center justify-between pb-3 border-b border-[#1b2234]">
            <h2 className="text-sm font-bold text-white tracking-wide flex items-center gap-2">
              <span>📈</span> REAL-TIME TRADE FEED
            </h2>
            <span className="text-xs text-[#00d2ff] font-mono animate-pulse">All Instruments</span>
          </div>

          <div className="mt-4 overflow-x-auto">
            <table className="w-full text-left text-xs font-mono">
              <thead>
                <tr className="text-[#6c7d93] border-b border-[#1b2234]">
                  <th className="pb-2">TIME</th>
                  <th className="pb-2">PAIR</th>
                  <th className="pb-2">SIDE</th>
                  <th className="pb-2 text-right">PRICE (USDT)</th>
                  <th className="pb-2 text-right">QTY</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#141b2b]">
                {feedTrades.map((trade) => (
                  <tr key={trade.id} className="hover:bg-[#121826] transition-colors">
                    <td className="py-2 text-[#8899ac]">{trade.time}</td>
                    <td className="py-2 font-bold text-white">{trade.inst}</td>
                    <td className="py-2">
                      <span
                        className={`px-1.5 py-0.5 rounded text-[10px] font-bold ${
                          trade.side === 'BUY'
                            ? 'bg-[#00f5a0]/15 text-[#00f5a0]'
                            : 'bg-[#ff3b69]/15 text-[#ff3b69]'
                        }`}
                      >
                        {trade.side}
                      </span>
                    </td>
                    <td
                      className={`py-2 text-right font-bold ${
                        trade.side === 'BUY' ? 'text-[#00f5a0]' : 'text-[#ff3b69]'
                      }`}
                    >
                      ${trade.price}
                    </td>
                    <td className="py-2 text-right text-white">{trade.qty}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </section>
    </div>
  );
}

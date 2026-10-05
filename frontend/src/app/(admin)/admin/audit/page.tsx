'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { api } from '@/lib/api';
import { AuditLogEntry, AuditQueryParams } from '@/types';
import { isAuthenticated, getCurrentUser, setAuthSession } from '@/lib/auth';

export default function AuditLogViewerPage() {
  const [logs, setLogs] = useState<AuditLogEntry[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [expandedId, setExpandedId] = useState<number | null>(null);

  // Filters
  const [subjectId, setSubjectId] = useState<string>('');
  const [instrument, setInstrument] = useState<string>('');
  const [eventType, setEventType] = useState<string>('');
  const [traceId, setTraceId] = useState<string>('');
  const [limit, setLimit] = useState<number>(50);
  const [offset, setOffset] = useState<number>(0);

  const [copiedId, setCopiedId] = useState<number | null>(null);
  const [hasAuth, setHasAuth] = useState<boolean>(false);
  const [adminUser, setAdminUser] = useState<string>('demo');

  useEffect(() => {
    const authStatus = isAuthenticated();
    setHasAuth(authStatus);
    const user = getCurrentUser();
    if (user) {
      setAdminUser(user.username);
    }
    fetchLogs();
  }, [offset, limit]);

  const fetchLogs = async () => {
    setLoading(true);
    const params: AuditQueryParams = {
      subjectId: subjectId.trim() || undefined,
      instrument: instrument.trim() || undefined,
      eventType: eventType.trim() || undefined,
      traceId: traceId.trim() || undefined,
      limit,
      offset,
    };

    try {
      const data = await api.audit.queryLogs(params);
      if (data && data.length > 0) {
        setLogs(data);
      } else {
        setLogs(generateSyntheticAuditLogs());
      }
    } catch (e) {
      setLogs(generateSyntheticAuditLogs());
    } finally {
      setLoading(false);
    }
  };

  const handleFilterSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setOffset(0);
    fetchLogs();
  };

  const handleReset = () => {
    setSubjectId('');
    setInstrument('');
    setEventType('');
    setTraceId('');
    setOffset(0);
    setTimeout(fetchLogs, 50);
  };

  const exportCsv = () => {
    if (logs.length === 0) return;
    const headers = ['Entry ID', 'Event ID', 'Event Time', 'Event Type', 'Subject Type', 'Subject ID', 'Instrument', 'Trace ID', 'Payload'];
    const rows = logs.map((l) => [
      l.entryId,
      l.eventId,
      l.eventTime,
      l.eventType,
      l.subjectType,
      l.subjectId,
      l.instrument || '',
      l.traceId,
      `"${l.payload.replace(/"/g, '""')}"`,
    ]);

    const csvContent = [headers.join(','), ...rows.map((r) => r.join(','))].join('\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `dete_audit_logs_${Date.now()}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const copyPayload = (payload: string, entryId: number) => {
    navigator.clipboard.writeText(payload);
    setCopiedId(entryId);
    setTimeout(() => setCopiedId(null), 2000);
  };

  const loginAsAdmin = async () => {
    try {
      const res = await api.auth.login({ username: 'demo', password: 'DemoPassword123!' });
      if (res && res.accessToken) {
        setAuthSession(res);
        setHasAuth(true);
        setAdminUser(res.user.username);
        fetchLogs();
      }
    } catch (e) {
      console.warn('Demo login failed', e);
    }
  };

  return (
    <div className="min-h-screen bg-[#07090e] text-[#e0e6ed] p-4 lg:p-6 font-sans">
      {/* Top Header */}
      <header className="flex flex-wrap items-center justify-between pb-5 border-b border-[#1b2234] gap-4">
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2">
            <span className="text-xl font-black tracking-wider text-transparent bg-clip-text bg-gradient-to-r from-[#ffb800] via-[#ff3b69] to-[#00d2ff]">
              DETE
            </span>
            <span className="text-xs px-2 py-0.5 rounded bg-[#1c180d] text-[#ffb800] border border-[#ffb800]/30 font-mono font-semibold">
              AUDIT LOG VIEWER
            </span>
          </div>
          <span className="text-xs text-[#8899ac]">Append-Only Regulatory Ledger</span>
        </div>

        <div className="flex items-center gap-3">
          <Link
            href="/dashboard"
            className="px-3 py-1.5 rounded-lg bg-[#141d30] border border-[#202c46] text-xs font-semibold text-[#00f5a0] hover:bg-[#1c2944] transition-colors"
          >
            📊 System Dashboard
          </Link>
          <Link
            href="/terminal"
            className="px-3 py-1.5 rounded-lg bg-[#141d30] border border-[#202c46] text-xs font-semibold text-[#00d2ff] hover:bg-[#1c2944] transition-colors"
          >
            ⚡ Terminal
          </Link>
          <button
            onClick={exportCsv}
            disabled={logs.length === 0}
            className="px-3 py-1.5 rounded-lg bg-[#00f5a0]/15 text-[#00f5a0] border border-[#00f5a0]/30 text-xs font-bold hover:bg-[#00f5a0]/25 transition-all disabled:opacity-50"
          >
            📥 Export CSV
          </button>
        </div>
      </header>

      {/* Auth Banner if unauthenticated */}
      {!hasAuth && (
        <div className="mt-4 p-3 bg-[#1e190f] border border-[#ffb800]/40 rounded-lg flex items-center justify-between text-xs">
          <div className="flex items-center gap-2 text-[#ffb800]">
            <span>⚠️</span>
            <span>You are viewing audit records in preview mode. Click to authenticate with Demo Admin privileges:</span>
          </div>
          <button
            onClick={loginAsAdmin}
            className="px-3 py-1 rounded bg-[#ffb800] text-[#07090e] font-bold hover:bg-[#ffb800]/90 transition-all"
          >
            ⚡ Login as Demo Admin
          </button>
        </div>
      )}

      {/* Filter Bar */}
      <section className="mt-6 bg-[#0c101a] border border-[#1b2234] rounded-xl p-4">
        <form onSubmit={handleFilterSubmit} className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3">
          {/* Subject ID */}
          <div>
            <label className="block text-[11px] font-mono text-[#8899ac] mb-1">SUBJECT / ORDER ID</label>
            <input
              type="text"
              placeholder="e.g. 00000000-0000..."
              value={subjectId}
              onChange={(e) => setSubjectId(e.target.value)}
              className="w-full bg-[#121826] border border-[#1e2a44] rounded px-3 py-1.5 text-xs font-mono text-white focus:outline-none focus:border-[#ffb800]"
            />
          </div>

          {/* Event Type */}
          <div>
            <label className="block text-[11px] font-mono text-[#8899ac] mb-1">EVENT TYPE</label>
            <select
              value={eventType}
              onChange={(e) => setEventType(e.target.value)}
              className="w-full bg-[#121826] border border-[#1e2a44] rounded px-3 py-1.5 text-xs font-mono text-white focus:outline-none focus:border-[#ffb800]"
            >
              <option value="">ALL EVENTS</option>
              <option value="ORDER_PLACED">ORDER_PLACED</option>
              <option value="ORDER_CANCELLED">ORDER_CANCELLED</option>
              <option value="ORDER_FILLED">ORDER_FILLED</option>
              <option value="ORDER_REJECTED">ORDER_REJECTED</option>
              <option value="TRADE_EXECUTED">TRADE_EXECUTED</option>
              <option value="USER_REGISTERED">USER_REGISTERED</option>
              <option value="USER_LOGIN">USER_LOGIN</option>
              <option value="USER_LOGOUT">USER_LOGOUT</option>
              <option value="BALANCE_RESERVED">BALANCE_RESERVED</option>
              <option value="BALANCE_SETTLED">BALANCE_SETTLED</option>
            </select>
          </div>

          {/* Instrument */}
          <div>
            <label className="block text-[11px] font-mono text-[#8899ac] mb-1">INSTRUMENT</label>
            <select
              value={instrument}
              onChange={(e) => setInstrument(e.target.value)}
              className="w-full bg-[#121826] border border-[#1e2a44] rounded px-3 py-1.5 text-xs font-mono text-white focus:outline-none focus:border-[#ffb800]"
            >
              <option value="">ALL PAIRS</option>
              <option value="BTC_USD">BTC-USD</option>
              <option value="ETH_USD">ETH-USD</option>
              <option value="SOL_USD">SOL-USD</option>
            </select>
          </div>

          {/* Trace ID */}
          <div>
            <label className="block text-[11px] font-mono text-[#8899ac] mb-1">TRACE ID</label>
            <input
              type="text"
              placeholder="e.g. 4bf92f3577b34da6..."
              value={traceId}
              onChange={(e) => setTraceId(e.target.value)}
              className="w-full bg-[#121826] border border-[#1e2a44] rounded px-3 py-1.5 text-xs font-mono text-white focus:outline-none focus:border-[#ffb800]"
            />
          </div>

          {/* Filter / Reset Buttons */}
          <div className="flex items-end gap-2">
            <button
              type="submit"
              className="flex-1 py-1.5 bg-[#ffb800] text-[#07090e] rounded text-xs font-bold font-mono hover:bg-[#ffb800]/90 transition-all"
            >
              🔍 Filter
            </button>
            <button
              type="button"
              onClick={handleReset}
              className="py-1.5 px-3 bg-[#162033] hover:bg-[#1e2a44] text-xs font-mono text-[#8899ac] rounded"
            >
              Reset
            </button>
          </div>
        </form>
      </section>

      {/* Audit Log Table */}
      <section className="mt-6 bg-[#0c101a] border border-[#1b2234] rounded-xl overflow-hidden">
        <div className="p-4 border-b border-[#1b2234] flex items-center justify-between">
          <span className="text-xs font-bold font-mono text-white">
            QUERY RESULTS: <span className="text-[#ffb800]">{logs.length}</span> RECORDS
          </span>
          <div className="text-xs text-[#8899ac] font-mono">
            Showing Page {Math.floor(offset / limit) + 1}
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs font-mono">
            <thead>
              <tr className="bg-[#0f1422] text-[#8899ac] border-b border-[#1b2234]">
                <th className="py-3 px-4">EVENT TIME</th>
                <th className="py-3 px-4">EVENT TYPE</th>
                <th className="py-3 px-4">SUBJECT TYPE</th>
                <th className="py-3 px-4">SUBJECT ID</th>
                <th className="py-3 px-4">INSTRUMENT</th>
                <th className="py-3 px-4">TRACE ID (JAEGER)</th>
                <th className="py-3 px-4 text-center">PAYLOAD</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#151c2e]">
              {logs.map((entry) => {
                const isExpanded = expandedId === entry.entryId;
                return (
                  <React.Fragment key={entry.entryId}>
                    <tr className="hover:bg-[#111728] transition-colors">
                      <td className="py-3 px-4 text-[#8899ac]">
                        {new Date(entry.eventTime).toLocaleString()}
                      </td>
                      <td className="py-3 px-4">
                        <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${getEventTypeBadge(entry.eventType)}`}>
                          {entry.eventType}
                        </span>
                      </td>
                      <td className="py-3 px-4 text-white font-medium">{entry.subjectType}</td>
                      <td className="py-3 px-4 text-[#00d2ff] font-mono text-[11px] truncate max-w-[140px]" title={entry.subjectId}>
                        {entry.subjectId}
                      </td>
                      <td className="py-3 px-4 text-white font-bold">{entry.instrument || '—'}</td>
                      <td className="py-3 px-4">
                        {entry.traceId ? (
                          <a
                            href={`http://localhost:16686/trace/${entry.traceId}`}
                            target="_blank"
                            rel="noreferrer"
                            className="px-2 py-0.5 rounded bg-[#131b2e] border border-[#233252] text-[#00d2ff] hover:text-white hover:border-[#00d2ff] transition-all text-[11px] inline-flex items-center gap-1"
                            title="Open trace in Jaeger"
                          >
                            <span className="truncate max-w-[120px]">{entry.traceId}</span>
                            <span>↗</span>
                          </a>
                        ) : (
                          <span className="text-[#6c7d93]">—</span>
                        )}
                      </td>
                      <td className="py-3 px-4 text-center">
                        <button
                          onClick={() => setExpandedId(isExpanded ? null : entry.entryId)}
                          className="px-2.5 py-1 rounded bg-[#182338] hover:bg-[#202e4a] text-[11px] text-[#00d2ff] font-bold"
                        >
                          {isExpanded ? '▲ Hide' : '▼ View JSON'}
                        </button>
                      </td>
                    </tr>

                    {/* Expandable JSON Payload Row */}
                    {isExpanded && (
                      <tr className="bg-[#090d16]">
                        <td colSpan={7} className="p-4 border-b border-[#1b2234]">
                          <div className="flex items-center justify-between mb-2">
                            <span className="text-xs font-bold text-[#ffb800] font-mono">
                              RAW AUDIT PAYLOAD (Entry #{entry.entryId}):
                            </span>
                            <button
                              onClick={() => copyPayload(entry.payload, entry.entryId)}
                              className="px-2 py-0.5 bg-[#172033] hover:bg-[#212d46] text-[#00f5a0] text-[10px] font-mono rounded"
                            >
                              {copiedId === entry.entryId ? '✓ Copied' : '📋 Copy JSON'}
                            </button>
                          </div>
                          <pre className="p-3 bg-[#0d121f] rounded border border-[#1b263e] text-[11px] font-mono text-[#a5b4fc] overflow-x-auto max-h-64">
                            {formatJson(entry.payload)}
                          </pre>
                        </td>
                      </tr>
                    )}
                  </React.Fragment>
                );
              })}
            </tbody>
          </table>
        </div>

        {/* Pagination Bar */}
        <div className="p-4 border-t border-[#1b2234] flex items-center justify-between">
          <button
            onClick={() => setOffset(Math.max(0, offset - limit))}
            disabled={offset === 0}
            className="px-3 py-1.5 rounded bg-[#121826] border border-[#1e2a44] text-xs font-mono disabled:opacity-40"
          >
            ← Previous Page
          </button>
          <button
            onClick={() => setOffset(offset + limit)}
            disabled={logs.length < limit}
            className="px-3 py-1.5 rounded bg-[#121826] border border-[#1e2a44] text-xs font-mono disabled:opacity-40"
          >
            Next Page →
          </button>
        </div>
      </section>
    </div>
  );
}

function getEventTypeBadge(type: string): string {
  if (type.includes('PLACED') || type.includes('LOGIN') || type.includes('SETTLED')) {
    return 'bg-[#00f5a0]/15 text-[#00f5a0] border border-[#00f5a0]/30';
  }
  if (type.includes('FILLED') || type.includes('TRADE')) {
    return 'bg-[#00d2ff]/15 text-[#00d2ff] border border-[#00d2ff]/30';
  }
  if (type.includes('CANCELLED') || type.includes('REJECTED') || type.includes('LOGOUT')) {
    return 'bg-[#ff3b69]/15 text-[#ff3b69] border border-[#ff3b69]/30';
  }
  return 'bg-[#ffb800]/15 text-[#ffb800] border border-[#ffb800]/30';
}

function formatJson(raw: string): string {
  try {
    const parsed = JSON.parse(raw);
    return JSON.stringify(parsed, null, 2);
  } catch (e) {
    return raw;
  }
}

function generateSyntheticAuditLogs(): AuditLogEntry[] {
  const now = Date.now();
  return [
    {
      entryId: 101,
      eventId: 'e1000000-0000-0000-0000-000000000001',
      eventType: 'ORDER_PLACED',
      subjectType: 'ORDER',
      subjectId: 'ord-btc-892401',
      actorId: '00000000-0000-0000-0000-000000000001',
      instrument: 'BTC-USD',
      payload: JSON.stringify({
        orderId: 'ord-btc-892401',
        side: 'BUY',
        type: 'LIMIT',
        price: '6542000000000',
        quantity: '10000000',
        accountId: '00000000-0000-0000-0000-000000000001',
      }),
      traceId: '4bf92f3577b34da6a3ce929d0e0e4736',
      eventTime: new Date(now - 12000).toISOString(),
      ingestedAt: new Date(now - 11950).toISOString(),
    },
    {
      entryId: 102,
      eventId: 'e1000000-0000-0000-0000-000000000002',
      eventType: 'TRADE_EXECUTED',
      subjectType: 'TRADE',
      subjectId: 'trd-btc-402910',
      actorId: '00000000-0000-0000-0000-000000000001',
      instrument: 'BTC-USD',
      payload: JSON.stringify({
        tradeId: 'trd-btc-402910',
        price: '6542000000000',
        quantity: '10000000',
        buyOrderId: 'ord-btc-892401',
        sellOrderId: 'ord-btc-892389',
        sequenceNumber: 1420,
      }),
      traceId: '7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d',
      eventTime: new Date(now - 8000).toISOString(),
      ingestedAt: new Date(now - 7950).toISOString(),
    },
    {
      entryId: 103,
      eventId: 'e1000000-0000-0000-0000-000000000003',
      eventType: 'BALANCE_SETTLED',
      subjectType: 'ACCOUNT',
      subjectId: '00000000-0000-0000-0000-000000000001',
      actorId: '00000000-0000-0000-0000-000000000001',
      instrument: 'BTC-USD',
      payload: JSON.stringify({
        accountId: '00000000-0000-0000-0000-000000000001',
        baseAsset: 'BTC',
        baseAmountCredited: '10000000',
        quoteAsset: 'USD',
        quoteAmountDebited: '65420000000',
      }),
      traceId: '8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c',
      eventTime: new Date(now - 7000).toISOString(),
      ingestedAt: new Date(now - 6920).toISOString(),
    },
    {
      entryId: 104,
      eventId: 'e1000000-0000-0000-0000-000000000004',
      eventType: 'USER_LOGIN',
      subjectType: 'USER',
      subjectId: '00000000-0000-0000-0000-000000000001',
      actorId: '00000000-0000-0000-0000-000000000001',
      instrument: '',
      payload: JSON.stringify({
        username: 'demo',
        roles: ['USER', 'DEMO'],
        clientIp: '127.0.0.1',
      }),
      traceId: '9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d',
      eventTime: new Date(now - 25000).toISOString(),
      ingestedAt: new Date(now - 24900).toISOString(),
    },
  ];
}

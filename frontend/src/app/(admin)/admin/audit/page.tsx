'use client';

import { Fragment, useCallback, useEffect, useState } from 'react';
import { Select } from '@/components/common/Select';
import { MARKET_OPTIONS } from '@/components/header/MarketSelector';
import { AppHeader } from '@/components/common/AppHeader';
import { Loader } from '@/components/common/Loader';
import { api } from '@/lib/api';
import { AuditLogEntry, AuditQueryParams } from '@/types';
import { Download, ExternalLink } from 'lucide-react';

const emptyFilters = {
  subjectId: '',
  instrument: '',
  eventType: '',
  traceId: '',
};

export default function AuditLogViewerPage() {
  const [logs, setLogs] = useState<AuditLogEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [copiedId, setCopiedId] = useState<number | null>(null);
  const [filters, setFilters] = useState(emptyFilters);
  const [query, setQuery] = useState<AuditQueryParams>({
    limit: 50,
    offset: 0,
  });
  const [revision, setRevision] = useState(0);
  const fetchLogs = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setLogs(await api.audit.queryLogs(query));
    } catch (error) {
      setLogs([]);
      setError(
        error instanceof Error
          ? error.message
          : 'Unable to load audit records.',
      );
    } finally {
      setLoading(false);
    }
  }, [query]);
  useEffect(() => {
    void fetchLogs();
  }, [fetchLogs, revision]);
  const exportCsv = () => {
    const cell = (value: unknown) =>
      `"${String(value ?? '').replace(/"/g, '""')}"`;
    const rows = [
      [
        'Entry ID',
        'Event ID',
        'Event time',
        'Event type',
        'Subject type',
        'Subject ID',
        'Instrument',
        'Trace ID',
        'Payload',
      ],
      ...logs.map((log) => [
        log.entryId,
        log.eventId,
        log.eventTime,
        log.eventType,
        log.subjectType,
        log.subjectId,
        log.instrument,
        log.traceId,
        log.payload,
      ]),
    ];
    const url = URL.createObjectURL(
      new Blob([rows.map((row) => row.map(cell).join(',')).join('\n')], {
        type: 'text/csv;charset=utf-8;',
      }),
    );
    const link = document.createElement('a');
    link.href = url;
    link.download = `dete-audit-${Date.now()}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  };
  const copyPayload = async (entry: AuditLogEntry) => {
    try {
      await navigator.clipboard.writeText(entry.payload);
      setCopiedId(entry.entryId);
    } catch {
      setError('Unable to copy. Select and copy the payload text below.');
    }
  };
  const updateFilter = (key: keyof typeof emptyFilters, value: string) =>
    setFilters((current) => ({ ...current, [key]: value }));
  return (
    <>
      <AppHeader />
      <main className="page-content">
        <div className="page-heading">
          <div>
            <span className="eyebrow">Exchange records</span>
            <h1>Audit log</h1>
            <p>
              Search events, inspect payloads, and trace activity across the
              exchange.
            </p>
          </div>
          <button
            className="button"
            disabled={loading || logs.length === 0}
            onClick={exportCsv}
          >
            <Download size={15} /> Export CSV
          </button>
        </div>
        <form
          className="panel filter-grid"
          onSubmit={(event) => {
            event.preventDefault();
            setQuery({
              ...Object.fromEntries(
                Object.entries(filters).map(([key, value]) => [
                  key,
                  value.trim() || undefined,
                ]),
              ),
              limit: query.limit,
              offset: 0,
            });
          }}
        >
          <div className="field">
            <label htmlFor="subject">Subject / order ID</label>
            <input
              id="subject"
              placeholder="Search subject ID"
              value={filters.subjectId}
              onChange={(e) => updateFilter('subjectId', e.target.value)}
            />
          </div>
          <div className="field">
            <label htmlFor="event-type">Event type</label>
            <Select
              id="event-type"
              label="Event type"
              variant="field"
              value={filters.eventType}
              onChange={(value) => updateFilter('eventType', value)}
              options={[
                { value: '', label: 'All events' },
                ...[
                  'ORDER_PLACED',
                  'ORDER_CANCELLED',
                  'ORDER_FILLED',
                  'ORDER_REJECTED',
                  'TRADE_EXECUTED',
                  'USER_REGISTERED',
                  'USER_LOGIN',
                  'USER_LOGOUT',
                  'BALANCE_RESERVED',
                  'BALANCE_SETTLED',
                ].map((value) => ({
                  value,
                  label: value
                    .toLowerCase()
                    .replaceAll('_', ' ')
                    .replace(/^./, (letter) => letter.toUpperCase()),
                })),
              ]}
            />
          </div>
          <div className="field">
            <label htmlFor="audit-instrument">Instrument</label>
            <Select
              id="audit-instrument"
              label="Instrument"
              variant="field"
              value={filters.instrument}
              onChange={(value) => updateFilter('instrument', value)}
              options={[{ value: '', label: 'All markets' }, ...MARKET_OPTIONS]}
            />
          </div>
          <div className="field">
            <label htmlFor="trace-id">Trace ID</label>
            <input
              id="trace-id"
              placeholder="Search trace ID"
              value={filters.traceId}
              onChange={(e) => updateFilter('traceId', e.target.value)}
            />
          </div>
          <div className="toolbar">
            <button className="button primary" disabled={loading}>
              Apply filters
            </button>
            <button
              type="button"
              className="button"
              onClick={() => {
                setFilters(emptyFilters);
                setQuery({ limit: 50, offset: 0 });
              }}
            >
              Reset
            </button>
          </div>
        </form>
        {error && (
          <div className="notice error" role="alert">
            <span>Audit records unavailable. {error}</span>
            <button
              className="button"
              onClick={() => setRevision((value) => value + 1)}
            >
              Retry
            </button>
          </div>
        )}
        <section
          className="panel"
          aria-label="Audit results"
          aria-busy={loading}
        >
          <div className="panel-heading">
            <h2>
              Event records <span className="count">{logs.length}</span>
            </h2>
            <span className="eyebrow">
              Page {Math.floor((query.offset ?? 0) / (query.limit ?? 50)) + 1}
            </span>
          </div>
          {loading ? (
            <Loader label="Loading audit records..." />
          ) : logs.length === 0 ? (
            <p className="empty-state">
              {error
                ? 'Records will appear when the audit service is available.'
                : 'No events match these filters. Try a broader search.'}
            </p>
          ) : (
            <div className="table-scroll">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Event time</th>
                    <th>Event type</th>
                    <th>Subject</th>
                    <th>Instrument</th>
                    <th>Trace</th>
                    <th>Payload</th>
                  </tr>
                </thead>
                <tbody>
                  {logs.map((entry) => (
                    <Fragment key={entry.entryId}>
                      <tr>
                        <td className="font-mono muted">
                          {new Date(entry.eventTime).toLocaleString()}
                        </td>
                        <td>
                          <span className="badge">{entry.eventType}</span>
                        </td>
                        <td>
                          <span className="muted">{entry.subjectType}</span>
                          <br />
                          <span className="font-mono">{entry.subjectId}</span>
                        </td>
                        <td>{entry.instrument || '—'}</td>
                        <td>
                          {entry.traceId ? (
                            <a
                              className="button"
                              href={`http://localhost:16686/trace/${encodeURIComponent(entry.traceId)}`}
                              target="_blank"
                              rel="noreferrer"
                              title={entry.traceId}
                            >
                              {entry.traceId.slice(0, 12)}…
                              <ExternalLink size={12} />
                            </a>
                          ) : (
                            '—'
                          )}
                        </td>
                        <td>
                          <button
                            className="button"
                            aria-expanded={expandedId === entry.entryId}
                            onClick={() =>
                              setExpandedId(
                                expandedId === entry.entryId
                                  ? null
                                  : entry.entryId,
                              )
                            }
                          >
                            {expandedId === entry.entryId
                              ? 'Hide JSON'
                              : 'View JSON'}
                          </button>
                        </td>
                      </tr>
                      {expandedId === entry.entryId && (
                        <tr>
                          <td colSpan={6}>
                            <div className="toolbar">
                              <strong>Event payload · #{entry.entryId}</strong>
                              <button
                                className="button"
                                onClick={() => copyPayload(entry)}
                              >
                                {copiedId === entry.entryId
                                  ? 'Copied'
                                  : 'Copy JSON'}
                              </button>
                            </div>
                            <pre className="payload">
                              {formatJson(entry.payload)}
                            </pre>
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <div className="pagination">
            <button
              className="button"
              disabled={loading || !query.offset}
              onClick={() =>
                setQuery((current) => ({
                  ...current,
                  offset: Math.max(
                    0,
                    (current.offset ?? 0) - (current.limit ?? 50),
                  ),
                }))
              }
            >
              Previous
            </button>
            <div className="toolbar muted">
              <label htmlFor="audit-page-size">Rows per page</label>
              <Select
                id="audit-page-size"
                label="Rows per page"
                value={String(query.limit ?? 50)}
                onChange={(value) =>
                  setQuery((current) => ({
                    ...current,
                    limit: Number(value),
                    offset: 0,
                  }))
                }
                options={[25, 50, 100].map((size) => ({
                  value: String(size),
                  label: String(size),
                }))}
              />
            </div>
            <button
              className="button"
              disabled={loading || logs.length < (query.limit ?? 50)}
              onClick={() =>
                setQuery((current) => ({
                  ...current,
                  offset: (current.offset ?? 0) + (current.limit ?? 50),
                }))
              }
            >
              Next
            </button>
          </div>
        </section>
      </main>
    </>
  );
}

function formatJson(raw: string) {
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
}

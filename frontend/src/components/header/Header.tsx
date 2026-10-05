'use client';

import React from 'react';
import { Instrument } from '../../types';
import { INSTRUMENT_METAS } from '../../lib/api';
import { ConnectionState } from '../../lib/ws';
import { Activity, ShieldCheck, LogOut, ChevronDown, Radio } from 'lucide-react';
import { getCurrentUser, clearAuthSession } from '../../lib/auth';
import { useRouter } from 'next/navigation';
import Link from 'next/link';

interface HeaderProps {
  selectedInstrument: Instrument;
  onSelectInstrument: (inst: Instrument) => void;
  connectionState: ConnectionState;
  latency: number;
}

export function Header({
  selectedInstrument,
  onSelectInstrument,
  connectionState,
  latency,
}: HeaderProps) {
  const router = useRouter();
  const currentUser = getCurrentUser();
  const meta = INSTRUMENT_METAS[selectedInstrument];

  const handleLogout = () => {
    clearAuthSession();
    router.push('/login');
  };

  const isConnected = connectionState === 'connected';
  const isPositiveChange = meta.change24h.startsWith('+');

  return (
    <header className="terminal-header">
      {/* Brand & Market Selector */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <div
            style={{
              width: 26,
              height: 26,
              borderRadius: 6,
              background: 'linear-gradient(135deg, #00d2ff 0%, #0077ff 100%)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              boxShadow: '0 0 12px rgba(0, 210, 255, 0.5)',
            }}
          >
            <Activity size={16} color="#ffffff" />
          </div>
          <div style={{ fontWeight: 800, letterSpacing: '0.05em', fontSize: '1rem' }}>
            <span style={{ color: '#ffffff' }}>DETE</span>
            <span style={{ color: 'var(--accent-cyan)', fontSize: '0.75rem', marginLeft: 4, fontWeight: 500 }}>
              TERMINAL
            </span>
          </div>
        </div>

        {/* Global Navigation Links */}
        <nav style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          <Link
            href="/terminal"
            style={{
              color: 'var(--accent-cyan)',
              fontSize: '0.74rem',
              fontWeight: 600,
              textDecoration: 'none',
              padding: '3px 8px',
              borderRadius: 4,
              backgroundColor: 'rgba(0, 210, 255, 0.12)',
            }}
          >
            Trading
          </Link>
          <Link
            href="/dashboard"
            style={{
              color: 'var(--text-secondary)',
              fontSize: '0.74rem',
              fontWeight: 600,
              textDecoration: 'none',
              padding: '3px 8px',
              borderRadius: 4,
              transition: 'color 0.15s ease',
            }}
          >
            Telemetry
          </Link>
          <Link
            href="/admin/audit"
            style={{
              color: 'var(--text-secondary)',
              fontSize: '0.74rem',
              fontWeight: 600,
              textDecoration: 'none',
              padding: '3px 8px',
              borderRadius: 4,
              transition: 'color 0.15s ease',
            }}
          >
            Audit
          </Link>
        </nav>

        {/* Instrument Dropdown Buttons */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            backgroundColor: 'var(--bg-input)',
            borderRadius: 6,
            padding: 2,
            border: '1px solid var(--border-subtle)',
          }}
        >
          {(['BTC_USDT', 'ETH_USDT', 'SOL_USDT'] as Instrument[]).map((inst) => {
            const active = inst === selectedInstrument;
            return (
              <button
                key={inst}
                onClick={() => onSelectInstrument(inst)}
                style={{
                  background: active ? 'var(--bg-hover)' : 'transparent',
                  color: active ? 'var(--accent-cyan)' : 'var(--text-secondary)',
                  border: 'none',
                  borderRadius: 4,
                  padding: '5px 10px',
                  fontSize: '0.75rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                  transition: 'all 0.15s ease',
                }}
              >
                {inst.replace('_', '/')}
              </button>
            );
          })}
        </div>
      </div>

      {/* 24h Ticker Tape */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 24, fontSize: '0.78rem' }}>
        <div>
          <span style={{ color: 'var(--text-muted)', marginRight: 6 }}>Last Price:</span>
          <span
            className="font-mono"
            style={{
              fontSize: '0.9rem',
              fontWeight: 700,
              color: isPositiveChange ? 'var(--bid-green)' : 'var(--ask-red)',
            }}
          >
            ${meta.lastPrice}
          </span>
        </div>

        <div>
          <span style={{ color: 'var(--text-muted)', marginRight: 6 }}>24h Change:</span>
          <span
            className="font-mono"
            style={{
              fontWeight: 600,
              color: isPositiveChange ? 'var(--bid-green)' : 'var(--ask-red)',
            }}
          >
            {meta.change24h}
          </span>
        </div>

        <div className="hidden-mobile">
          <span style={{ color: 'var(--text-muted)', marginRight: 6 }}>24h High:</span>
          <span className="font-mono" style={{ color: 'var(--text-primary)' }}>
            ${meta.high24h}
          </span>
        </div>

        <div className="hidden-mobile">
          <span style={{ color: 'var(--text-muted)', marginRight: 6 }}>24h Low:</span>
          <span className="font-mono" style={{ color: 'var(--text-primary)' }}>
            ${meta.low24h}
          </span>
        </div>

        <div className="hidden-mobile">
          <span style={{ color: 'var(--text-muted)', marginRight: 6 }}>24h Volume:</span>
          <span className="font-mono" style={{ color: 'var(--text-secondary)' }}>
            {meta.volume24h}
          </span>
        </div>
      </div>

      {/* WS Status, Latency & User Info */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
        {/* WS Connection Indicator */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 6,
            backgroundColor: 'var(--bg-input)',
            padding: '4px 10px',
            borderRadius: 14,
            border: '1px solid var(--border-subtle)',
            fontSize: '0.72rem',
          }}
        >
          <div
            className={isConnected ? 'pulse-live' : ''}
            style={{
              width: 8,
              height: 8,
              borderRadius: '50%',
              backgroundColor: isConnected ? 'var(--bid-green)' : 'var(--ask-red)',
            }}
          />
          <span style={{ color: isConnected ? 'var(--bid-green)' : 'var(--ask-red)', fontWeight: 600 }}>
            {isConnected ? 'WS LIVE' : 'WS RECONNECTING'}
          </span>
        </div>

        {/* Latency Monitor */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 5,
            fontSize: '0.72rem',
            color: 'var(--text-secondary)',
          }}
        >
          <Radio size={13} color="var(--accent-cyan)" />
          <span className="font-mono" style={{ color: latency < 50 ? 'var(--bid-green)' : 'var(--accent-amber)' }}>
            {latency}ms
          </span>
        </div>

        {/* User Account / Logout */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              background: 'var(--bg-input)',
              border: '1px solid var(--border-subtle)',
              padding: '4px 10px',
              borderRadius: 6,
              fontSize: '0.75rem',
            }}
          >
            <ShieldCheck size={14} color="var(--accent-cyan)" />
            <span style={{ fontWeight: 600, color: 'var(--text-primary)' }}>
              {currentUser?.username || 'demo'}
            </span>
          </div>

          <button
            onClick={handleLogout}
            title="Log Out"
            style={{
              background: 'none',
              border: 'none',
              color: 'var(--text-muted)',
              cursor: 'pointer',
              padding: 4,
              display: 'flex',
              alignItems: 'center',
              transition: 'color 0.15s ease',
            }}
            onMouseEnter={(e) => ((e.currentTarget as HTMLElement).style.color = 'var(--ask-red)')}
            onMouseLeave={(e) => ((e.currentTarget as HTMLElement).style.color = 'var(--text-muted)')}
          >
            <LogOut size={16} />
          </button>
        </div>
      </div>
    </header>
  );
}

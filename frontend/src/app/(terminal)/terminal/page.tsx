'use client';

import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Instrument, OrderSide, OrderType } from '../../../types';
import { isAuthenticated, setCustomUserSession } from '../../../lib/auth';
import { useWebSocket } from '../../../hooks/useWebSocket';
import { useAccount } from '../../../hooks/useAccount';
import { Header } from '../../../components/header/Header';
import { OrderBook } from '../../../components/orderbook/OrderBook';
import { TradeTape } from '../../../components/tradetape/TradeTape';
import { PriceChart } from '../../../components/chart/PriceChart';
import { OrderEntry } from '../../../components/orderentry/OrderEntry';
import { OpenOrdersTable } from '../../../components/orders/OpenOrdersTable';
import { OrderHistoryTable } from '../../../components/orders/OrderHistoryTable';
import { TradeHistoryTable } from '../../../components/orders/TradeHistoryTable';
import { AccountPanel } from '../../../components/account/AccountPanel';
import { ToastProvider } from '../../../components/common/ToastContext';
import { ReplayViewer } from '../../../components/replay/ReplayViewer';

type BottomTab = 'open' | 'history' | 'trades' | 'replay';

export default function TerminalPage() {
  const router = useRouter();
  const [selectedInstrument, setSelectedInstrument] = useState<Instrument>('BTC_USDT');
  const [selectedPriceFromBook, setSelectedPriceFromBook] = useState<string | undefined>(undefined);
  const [activeBottomTab, setActiveBottomTab] = useState<BottomTab>('open');

  const { connectionState, latency } = useWebSocket();
  const { balances, orders, tradeHistory, placeOrder, cancelOrder } = useAccount();

  // Protect terminal route: redirect to /login if not authenticated
  useEffect(() => {
    if (!isAuthenticated()) {
      // In development, auto-seed demo session so terminal is directly accessible
      setCustomUserSession('demo-jwt-token', {
        userId: 'demo-user-id',
        username: 'demo',
        email: 'demo@dete.io',
        roles: ['ROLE_TRADER'],
      });
    }

    if (typeof window !== 'undefined') {
      const params = new URLSearchParams(window.location.search);
      if (params.get('tab') === 'replay') {
        setActiveBottomTab('replay');
      }
    }
  }, [router]);

  const openOrdersCount = orders.filter(
    (o) => o.status === 'SUBMITTED' || o.status === 'ACCEPTED' || o.status === 'PARTIALLY_FILLED'
  ).length;

  return (
    <ToastProvider>
      <div className="terminal-layout">
        {/* Top Navigation & Market Ticker */}
        <Header
          selectedInstrument={selectedInstrument}
          onSelectInstrument={(inst) => {
            setSelectedInstrument(inst);
            setSelectedPriceFromBook(undefined);
          }}
          connectionState={connectionState}
          latency={latency}
        />

        {/* Main Trading Terminal Grid */}
        <main className="terminal-body">
          {/* Column 1: Order Book (top) & Trade Tape (bottom) */}
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 6,
              height: '100%',
              overflow: 'hidden',
            }}
          >
            <div style={{ flex: '1 1 58%', minHeight: 0 }}>
              <OrderBook
                instrument={selectedInstrument}
                onSelectPrice={(p) => setSelectedPriceFromBook(p)}
              />
            </div>
            <div style={{ flex: '1 1 42%', minHeight: 0 }}>
              <TradeTape instrument={selectedInstrument} />
            </div>
          </div>

          {/* Column 2: Candlestick Price Chart */}
          <div style={{ height: '100%', minHeight: 0, overflow: 'hidden' }}>
            <PriceChart instrument={selectedInstrument} />
          </div>

          {/* Column 3: Order Entry Form */}
          <div style={{ height: '100%', minHeight: 0, overflow: 'hidden' }}>
            <OrderEntry
              instrument={selectedInstrument}
              selectedPriceFromBook={selectedPriceFromBook}
              balances={balances}
              onPlaceOrder={placeOrder}
            />
          </div>

          {/* Bottom Row Span (Cols 1 & 2): Order Management Tabs */}
          <div
            className="glass-panel"
            style={{
              gridColumn: '1 / 3',
              height: '100%',
              minHeight: 0,
              display: 'flex',
              flexDirection: 'column',
              backgroundColor: 'var(--bg-panel)',
            }}
          >
            {/* Tabs Selector Bar */}
            <div
              style={{
                height: 34,
                borderBottom: '1px solid var(--border-subtle)',
                display: 'flex',
                alignItems: 'center',
                padding: '0 8px',
                gap: 4,
              }}
            >
              <button
                onClick={() => setActiveBottomTab('open')}
                style={{
                  background: activeBottomTab === 'open' ? 'var(--bg-hover)' : 'transparent',
                  color: activeBottomTab === 'open' ? 'var(--accent-cyan)' : 'var(--text-secondary)',
                  border: 'none',
                  borderRadius: 4,
                  padding: '4px 10px',
                  fontSize: '0.74rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                }}
              >
                <span>Open Orders</span>
                {openOrdersCount > 0 && (
                  <span
                    style={{
                      backgroundColor: 'var(--accent-cyan)',
                      color: '#071824',
                      fontSize: '0.62rem',
                      padding: '1px 5px',
                      borderRadius: 10,
                      fontWeight: 700,
                    }}
                  >
                    {openOrdersCount}
                  </span>
                )}
              </button>

              <button
                onClick={() => setActiveBottomTab('history')}
                style={{
                  background: activeBottomTab === 'history' ? 'var(--bg-hover)' : 'transparent',
                  color: activeBottomTab === 'history' ? 'var(--accent-cyan)' : 'var(--text-secondary)',
                  border: 'none',
                  borderRadius: 4,
                  padding: '4px 10px',
                  fontSize: '0.74rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                }}
              >
                Order History
              </button>

              <button
                onClick={() => setActiveBottomTab('trades')}
                style={{
                  background: activeBottomTab === 'trades' ? 'var(--bg-hover)' : 'transparent',
                  color: activeBottomTab === 'trades' ? 'var(--accent-cyan)' : 'var(--text-secondary)',
                  border: 'none',
                  borderRadius: 4,
                  padding: '4px 10px',
                  fontSize: '0.74rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                }}
              >
                Trade Fills
              </button>

              <button
                onClick={() => setActiveBottomTab('replay')}
                style={{
                  background: activeBottomTab === 'replay' ? 'var(--bg-hover)' : 'transparent',
                  color: activeBottomTab === 'replay' ? 'var(--accent-cyan)' : 'var(--text-secondary)',
                  border: 'none',
                  borderRadius: 4,
                  padding: '4px 10px',
                  fontSize: '0.74rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                }}
              >
                <span>Historical Replay</span>
                <span
                  style={{
                    backgroundColor: 'rgba(0, 210, 255, 0.15)',
                    color: 'var(--accent-cyan)',
                    fontSize: '0.62rem',
                    padding: '1px 5px',
                    borderRadius: 4,
                    fontWeight: 700,
                  }}
                >
                  L2
                </span>
              </button>
            </div>

            {/* Tab Body */}
            <div style={{ flex: 1, overflow: 'hidden', padding: 4 }}>
              {activeBottomTab === 'open' && (
                <OpenOrdersTable orders={orders} onCancelOrder={cancelOrder} />
              )}
              {activeBottomTab === 'history' && <OrderHistoryTable orders={orders} />}
              {activeBottomTab === 'trades' && <TradeHistoryTable trades={tradeHistory} />}
              {activeBottomTab === 'replay' && (
                <div style={{ height: '100%', overflowY: 'auto' }}>
                  <ReplayViewer instrument={selectedInstrument} />
                </div>
              )}
            </div>
          </div>

          {/* Bottom Row Col 3: Account Balances Panel */}
          <div
            className="glass-panel"
            style={{
              gridColumn: '3 / 4',
              height: '100%',
              minHeight: 0,
              backgroundColor: 'var(--bg-panel)',
            }}
          >
            <AccountPanel balances={balances} />
          </div>
        </main>
      </div>
    </ToastProvider>
  );
}

'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Instrument } from '@/types';
import { isAuthenticated } from '@/lib/auth';
import { useWebSocket } from '@/hooks/useWebSocket';
import { useAccount } from '@/hooks/useAccount';
import { Header } from '@/components/header/Header';
import { OrderBook } from '@/components/orderbook/OrderBook';
import { TradeTape } from '@/components/tradetape/TradeTape';
import { PriceChart } from '@/components/chart/PriceChart';
import { OrderEntry } from '@/components/orderentry/OrderEntry';
import { OpenOrdersTable } from '@/components/orders/OpenOrdersTable';
import { OrderHistoryTable } from '@/components/orders/OrderHistoryTable';
import { TradeHistoryTable } from '@/components/orders/TradeHistoryTable';
import { AccountPanel } from '@/components/account/AccountPanel';
import { ToastProvider } from '@/components/common/ToastContext';
import { ReplayViewer } from '@/components/replay/ReplayViewer';
import { Loader } from '@/components/common/Loader';

type ActivityTab = 'open' | 'history' | 'trades' | 'replay';
const tabs: [ActivityTab, string][] = [
  ['open', 'Open orders'],
  ['history', 'Order history'],
  ['trades', 'Trade fills'],
  ['replay', 'Historical replay'],
];

export default function TerminalPage() {
  const router = useRouter();
  const [ready, setReady] = useState(false);

  useEffect(() => {
    if (!isAuthenticated()) {
      router.replace('/login?returnUrl=/terminal');
      return;
    }
    setReady(true);
  }, [router]);
  return (
    <ToastProvider>
      {ready ? (
        <TradingWorkspace />
      ) : (
        <Loader variant="page" label="Loading trading workspace..." />
      )}
    </ToastProvider>
  );
}

function TradingWorkspace() {
  const [instrument, setInstrument] = useState<Instrument>('BTC_USD');
  const [selectedPrice, setSelectedPrice] = useState<string>();
  const [activeTab, setActiveTab] = useState<ActivityTab>('open');
  const [marketTab, setMarketTab] = useState<'book' | 'tape'>('book');
  const { connectionState, latency } = useWebSocket();
  const {
    balances,
    orders,
    orderHistory,
    tradeHistory,
    placeOrder,
    cancelOrder,
  } = useAccount();
  useEffect(() => {
    if (new URLSearchParams(window.location.search).get('tab') === 'replay')
      setActiveTab('replay');
  }, []);
  const openCount = orders.filter((o) =>
    ['SUBMITTED', 'ACCEPTED', 'PARTIALLY_FILLED'].includes(o.status),
  ).length;
  return (
    <div className="terminal-layout">
      <a className="skip-link" href="#workspace">
        Skip to trading workspace
      </a>
      <Header
        selectedInstrument={instrument}
        onSelectInstrument={(value) => {
          setInstrument(value);
          setSelectedPrice(undefined);
        }}
        connectionState={connectionState}
        latency={latency}
      />
      <main id="workspace" className="terminal-body">
        <section className="chart-region" aria-label="Price chart">
          <PriceChart key={instrument} instrument={instrument} />
        </section>
        <section className="panel depth-region" aria-label="Market activity">
          <div className="tab-bar" aria-label="Market view">
            <button
              aria-pressed={marketTab === 'book'}
              onClick={() => setMarketTab('book')}
            >
              Order book
            </button>
            <button
              aria-pressed={marketTab === 'tape'}
              onClick={() => setMarketTab('tape')}
            >
              Recent trades
            </button>
          </div>
          <div className="panel-body">
            {marketTab === 'book' ? (
              <OrderBook
                instrument={instrument}
                onSelectPrice={setSelectedPrice}
              />
            ) : (
              <TradeTape instrument={instrument} />
            )}
          </div>
        </section>
        <section className="entry-region" aria-label="Place an order">
          <OrderEntry
            key={instrument}
            instrument={instrument}
            selectedPriceFromBook={selectedPrice}
            balances={balances}
            onPlaceOrder={placeOrder}
          />
        </section>
        <section
          className="panel activity-region"
          aria-label="Account activity"
        >
          <div className="tab-bar" aria-label="Activity view">
            {tabs.map(([id, label]) => (
              <button
                key={id}
                aria-pressed={activeTab === id}
                onClick={() => setActiveTab(id)}
              >
                {label}
                {id === 'open' && <span className="count">{openCount}</span>}
              </button>
            ))}
          </div>
          <div className="panel-body activity-body">
            {activeTab === 'open' && (
              <OpenOrdersTable orders={orders} onCancelOrder={cancelOrder} />
            )}
            {activeTab === 'history' && (
              <OrderHistoryTable orders={orderHistory} />
            )}
            {activeTab === 'trades' && (
              <TradeHistoryTable trades={tradeHistory} />
            )}
            {activeTab === 'replay' && (
              <ReplayViewer key={instrument} instrument={instrument} />
            )}
          </div>
        </section>
        <section className="panel balance-region" aria-label="Account balances">
          <AccountPanel balances={balances} />
        </section>
      </main>
      <footer className="workspace-footer">
        <span>DETE / Spot trading workspace</span>
        <span>Prices and balances may include demonstration data.</span>
      </footer>
    </div>
  );
}

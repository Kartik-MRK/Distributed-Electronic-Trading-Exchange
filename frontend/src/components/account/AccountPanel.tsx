'use client';
import { BalanceRecord } from '@/types';
import { Wallet } from 'lucide-react';
export function AccountPanel({ balances }: { balances: BalanceRecord[] }) {
  return (
    <>
      <div className="panel-heading">
        <h2>
          <Wallet size={15} /> Balances
        </h2>
        <span className="eyebrow">Spot account</span>
      </div>
      <div className="balance-list">
        <div className="balance-row balance-labels">
          <span>Asset</span>
          <span>Available</span>
          <span>Reserved</span>
        </div>
        {balances.map((balance) => (
          <div className="balance-row" key={balance.asset}>
            <strong>{balance.asset}</strong>
            <span className="font-mono">
              {(balance.availableDisplay !== undefined
                ? balance.availableDisplay
                : Number(balance.available) >= 100_000_000
                  ? Number(balance.available) / 100_000_000
                  : Number(balance.available)
              ).toLocaleString('en-US', {
                minimumFractionDigits: balance.asset === 'USD' || balance.asset === 'USDT' ? 2 : 4,
                maximumFractionDigits: balance.asset === 'USD' || balance.asset === 'USDT' ? 2 : 6,
              })}
            </span>
            <span className="font-mono muted">
              {(balance.reservedDisplay !== undefined
                ? balance.reservedDisplay
                : Number(balance.reserved) >= 100_000_000
                  ? Number(balance.reserved) / 100_000_000
                  : Number(balance.reserved)
              ).toLocaleString('en-US', {
                minimumFractionDigits: balance.asset === 'USD' || balance.asset === 'USDT' ? 2 : 4,
                maximumFractionDigits: balance.asset === 'USD' || balance.asset === 'USDT' ? 2 : 6,
              })}
            </span>
          </div>
        ))}
        {balances.length === 0 && (
          <p className="empty-state">No balances to display.</p>
        )}
      </div>
    </>
  );
}

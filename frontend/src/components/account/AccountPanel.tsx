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
              {Number(balance.available).toLocaleString('en-US', {
                maximumFractionDigits: 6,
              })}
            </span>
            <span className="font-mono muted">
              {Number(balance.reserved).toLocaleString('en-US', {
                maximumFractionDigits: 6,
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

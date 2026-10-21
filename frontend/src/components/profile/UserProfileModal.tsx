'use client';

import React, { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  X,
  Copy,
  Check,
  ShieldCheck,
  Sparkles,
  Wallet,
  ArrowUpRight,
  LogOut,
  Calendar,
  Mail,
  User,
  Hash,
} from 'lucide-react';
import { getCurrentUser, clearAuthSession } from '@/lib/auth';
import { api, INITIAL_DEMO_BALANCES } from '@/lib/api';
import { BalanceRecord, UserRecord } from '@/types';

interface UserProfileModalProps {
  isOpen: boolean;
  onClose: () => void;
  onRefreshBalances?: () => void;
}

export function UserProfileModal({
  isOpen,
  onClose,
  onRefreshBalances,
}: UserProfileModalProps) {
  const router = useRouter();
  const [user, setUser] = useState<UserRecord | null>(null);
  const [balances, setBalances] = useState<BalanceRecord[]>([]);
  const [isCopied, setIsCopied] = useState(false);
  const [isLoadingBalances, setIsLoadingBalances] = useState(false);
  const [actionNotice, setActionNotice] = useState<string | null>(null);

  useEffect(() => {
    if (isOpen) {
      const currentUser = getCurrentUser();
      setUser(currentUser);
      fetchBalances();
    }
  }, [isOpen]);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isOpen) {
        onClose();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  const fetchBalances = async () => {
    setIsLoadingBalances(true);
    try {
      const data = await api.account.getBalances();
      if (data && data.length > 0) {
        setBalances(data);
      } else {
        setBalances(INITIAL_DEMO_BALANCES);
      }
    } catch {
      setBalances(INITIAL_DEMO_BALANCES);
    } finally {
      setIsLoadingBalances(false);
    }
  };

  if (!isOpen) return null;

  const handleCopyAccountId = async () => {
    const idToCopy = user?.userId || 'unknown-id';
    try {
      await navigator.clipboard.writeText(idToCopy);
      setIsCopied(true);
      setTimeout(() => setIsCopied(false), 2000);
    } catch {
      // Fallback
      setIsCopied(true);
      setTimeout(() => setIsCopied(false), 2000);
    }
  };

  const handleSignOut = () => {
    clearAuthSession();
    onClose();
    router.push('/login');
  };

  const handleDepositMockFunds = async () => {
    setActionNotice('Funded +10,000 USDT liquidity to trading balance!');
    setTimeout(() => {
      setActionNotice(null);
      fetchBalances();
      if (onRefreshBalances) onRefreshBalances();
    }, 2000);
  };

  const usdtBalance = balances.find((b) => b.asset === 'USDT') || balances.find((b) => b.asset === 'USD') || {
    asset: 'USDT',
    available: '50000.00',
    reserved: '0.00',
    total: '50000.00',
  };

  const getBalNum = (b: BalanceRecord | undefined, field: 'available' | 'reserved' | 'total'): number => {
    if (!b) return 0;
    if (field === 'available' && b.availableDisplay !== undefined) return b.availableDisplay;
    if (field === 'reserved' && b.reservedDisplay !== undefined) return b.reservedDisplay;
    if (field === 'total' && b.totalDisplay !== undefined) return b.totalDisplay;
    const num = Number(b[field] || 0);
    return num >= 100_000_000 ? num / 100_000_000 : num;
  };

  const isGuest = user?.isDemo || user?.role === 'ROLE_GUEST' || user?.username?.startsWith('guest_');
  const isVerified = user?.isVerified || !isGuest;
  const displayName = user?.fullName || user?.username || 'Trader';
  const displayEmail = user?.email || (isGuest ? 'Ephemeral Session (Demo)' : 'Not Provided');
  const displayDob = user?.dateOfBirth ? new Date(user.dateOfBirth).toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  }) : (isGuest ? 'Not required for Guest' : 'Verified Tier 1');

  const initials = displayName
    .split(' ')
    .map((n) => n[0])
    .filter(Boolean)
    .slice(0, 2)
    .join('')
    .toUpperCase() || 'TR';

  return (
    <div
      className="profile-modal-backdrop"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
      role="dialog"
      aria-modal="true"
      aria-labelledby="profile-modal-title"
    >
      <div className="profile-modal-card">
        {/* Modal Header */}
        <div className="profile-modal-header">
          <div className="profile-header-meta">
            <div className="profile-avatar-circle">
              {initials}
            </div>
            <div>
              <h2 id="profile-modal-title" className="profile-name">
                {displayName}
              </h2>
              <div className="profile-badge-row">
                {isGuest ? (
                  <span className="profile-pill pill-guest">
                    <Sparkles size={12} />
                    <span>Guest Demo Account</span>
                  </span>
                ) : isVerified ? (
                  <span className="profile-pill pill-verified">
                    <ShieldCheck size={12} />
                    <span>Tier 1 Verified Trader</span>
                  </span>
                ) : (
                  <span className="profile-pill pill-neutral">
                    <span>Trader Account</span>
                  </span>
                )}
                <span className="profile-pill pill-neutral">Spot Exchange</span>
              </div>
            </div>
          </div>
          <button
            className="profile-close-btn"
            onClick={onClose}
            aria-label="Close profile"
            title="Close"
          >
            <X size={18} />
          </button>
        </div>

        {actionNotice && (
          <div className="notice" role="status" style={{ margin: '14px 20px 0' }}>
            {actionNotice}
          </div>
        )}

        {/* Modal Body */}
        <div className="profile-modal-body">
          {/* Account Details Section */}
          <div className="profile-section">
            <span className="eyebrow" style={{ marginBottom: 8, display: 'block' }}>
              Trader Identification
            </span>
            <div className="profile-info-grid">
              <div className="profile-info-item">
                <span className="profile-info-label">
                  <Hash size={13} />
                  <span>Account UUID</span>
                </span>
                <div className="profile-copy-box">
                  <span className="font-mono profile-uuid-text">
                    {user?.userId || '00000000-0000-0000-0000-000000000000'}
                  </span>
                  <button
                    className="profile-copy-btn"
                    onClick={handleCopyAccountId}
                    title="Copy Account ID"
                    aria-label="Copy Account ID"
                  >
                    {isCopied ? <Check size={14} color="var(--bid-green)" /> : <Copy size={14} />}
                    <span>{isCopied ? 'Copied' : 'Copy'}</span>
                  </button>
                </div>
              </div>

              <div className="profile-info-item">
                <span className="profile-info-label">
                  <Mail size={13} />
                  <span>Email Address</span>
                </span>
                <span className="profile-info-value">{displayEmail}</span>
              </div>

              <div className="profile-info-item">
                <span className="profile-info-label">
                  <User size={13} />
                  <span>Username / Handle</span>
                </span>
                <span className="profile-info-value font-mono">@{user?.username || 'trader'}</span>
              </div>

              <div className="profile-info-item">
                <span className="profile-info-label">
                  <Calendar size={13} />
                  <span>Date of Birth / KYC</span>
                </span>
                <span className="profile-info-value">{displayDob}</span>
              </div>
            </div>
          </div>

          {/* Portfolio & USDT Liquidity Snapshot */}
          <div className="profile-section">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
              <span className="eyebrow">Portfolio Liquidity</span>
              <span className="profile-subtext">
                {isLoadingBalances ? 'Updating balances...' : 'Pre-funded exchange liquidity'}
              </span>
            </div>

            <div className="profile-balance-card">
              <div className="profile-balance-main">
                <div className="profile-balance-tag">
                  <Wallet size={16} />
                  <span>Total USDT Equity</span>
                </div>
                <div className="profile-balance-figure font-mono">
                  ${(getBalNum(usdtBalance, 'total') || getBalNum(usdtBalance, 'available')).toLocaleString('en-US', {
                    minimumFractionDigits: 2,
                    maximumFractionDigits: 2,
                  })}
                  <span className="profile-currency-label">USDT</span>
                </div>
              </div>

              <div className="profile-balance-split">
                <div>
                  <span className="profile-split-label">Available for Trading</span>
                  <span className="profile-split-val font-mono positive">
                    {getBalNum(usdtBalance, 'available').toLocaleString('en-US', {
                      minimumFractionDigits: 2,
                      maximumFractionDigits: 2,
                    })} USDT
                  </span>
                </div>
                <div>
                  <span className="profile-split-label">In Open Orders</span>
                  <span className="profile-split-val font-mono muted">
                    {getBalNum(usdtBalance, 'reserved').toLocaleString('en-US', {
                      minimumFractionDigits: 2,
                      maximumFractionDigits: 2,
                    })} USDT
                  </span>
                </div>
              </div>
            </div>
          </div>

          {/* Other Balances */}
          {balances.filter((b) => b.asset !== 'USDT' && b.asset !== 'USD').length > 0 && (
            <div className="profile-section">
              <span className="eyebrow" style={{ marginBottom: 8, display: 'block' }}>
                Crypto Holdings
              </span>
              <div className="profile-crypto-list">
                {balances
                  .filter((b) => b.asset !== 'USDT' && b.asset !== 'USD')
                  .map((b) => (
                    <div key={b.asset} className="profile-crypto-row">
                      <span className="profile-crypto-asset">{b.asset}</span>
                      <span className="font-mono">
                        {getBalNum(b, 'available').toLocaleString('en-US', { maximumFractionDigits: 4 })}
                      </span>
                    </div>
                  ))}
              </div>
            </div>
          )}
        </div>

        {/* Modal Footer */}
        <div className="profile-modal-footer">
          <button
            className="button profile-action-btn"
            onClick={handleDepositMockFunds}
            title="Request additional demo USDT funds"
          >
            <ArrowUpRight size={14} />
            <span>Deposit Test USDT</span>
          </button>
          <button
            className="button profile-signout-btn"
            onClick={handleSignOut}
            title="Sign out of DETE"
          >
            <LogOut size={14} />
            <span>Sign out</span>
          </button>
        </div>
      </div>
    </div>
  );
}

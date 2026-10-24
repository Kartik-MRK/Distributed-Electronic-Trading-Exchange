'use client';

import React, { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  X,
  Copy,
  Check,
  Wallet,
  RefreshCw,
  LogOut,
  Calendar,
  Mail,
  User,
  Hash,
} from 'lucide-react';
import { getCurrentUser, clearAuthSession } from '@/lib/auth';
import { api } from '@/lib/api';
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
  const cardRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (isOpen) {
      const currentUser = getCurrentUser();
      setUser(currentUser);
      fetchBalances();
    }
  }, [isOpen]);

  useEffect(() => {
    if (!isOpen) return;
    const previousFocus = document.activeElement as HTMLElement | null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    cardRef.current?.querySelector<HTMLButtonElement>('button')?.focus();
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        onClose();
      } else if (e.key === 'Tab') {
        const controls = cardRef.current?.querySelectorAll<HTMLElement>(
          'button:not(:disabled), a[href], input:not(:disabled), [tabindex="0"]',
        );
        if (!controls?.length) return;
        const first = controls[0];
        const last = controls[controls.length - 1];
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = previousOverflow;
      previousFocus?.focus();
    };
  }, [isOpen, onClose]);

  const fetchBalances = async () => {
    setIsLoadingBalances(true);
    try {
      const data = await api.account.getBalances();
      if (data && data.length > 0) {
        setBalances(data);
      } else {
        setBalances([]);
      }
    } catch {
      setBalances([]);
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
      setActionNotice(
        'Could not copy the account ID. Please select and copy it manually.',
      );
    }
  };

  const handleSignOut = () => {
    clearAuthSession();
    onClose();
    router.push('/login');
  };

  const handleRefreshBalances = async () => {
    setActionNotice(null);
    await fetchBalances();
    onRefreshBalances?.();
  };

  const usdtBalance = balances.find((b) => b.asset === 'USDT') ||
    balances.find((b) => b.asset === 'USD') || {
      asset: 'USDT',
      available: '50000.00',
      reserved: '0.00',
      total: '50000.00',
    };

  const getBalNum = (
    b: BalanceRecord | undefined,
    field: 'available' | 'reserved' | 'total',
  ): number => {
    if (!b) return 0;
    if (field === 'available' && b.availableDisplay !== undefined)
      return b.availableDisplay;
    if (field === 'reserved' && b.reservedDisplay !== undefined)
      return b.reservedDisplay;
    if (field === 'total' && b.totalDisplay !== undefined)
      return b.totalDisplay;
    const num = Number(b[field] || 0);
    return num >= 100_000_000 ? num / 100_000_000 : num;
  };

  const isGuest =
    user?.role === 'ROLE_GUEST' ||
    user?.roles?.includes('ROLE_GUEST') ||
    user?.username?.startsWith('guest_');
  const isDemo = user?.isDemo || isGuest;
  const displayName = user?.fullName || user?.username || 'Trader';
  const displayEmail = isGuest
    ? 'Not linked to an email'
    : user?.email || 'Not provided';
  const displayDob = user?.dateOfBirth
    ? new Date(user.dateOfBirth).toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
      })
    : 'Not provided';

  const initials =
    displayName
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
      <div className="profile-modal-card" ref={cardRef}>
        {/* Modal Header */}
        <div className="profile-modal-header">
          <div className="profile-header-meta">
            <div className="profile-avatar-circle">{initials}</div>
            <div>
              <h2 id="profile-modal-title" className="profile-name">
                {displayName}
              </h2>
              <p className="profile-account-type">
                {isGuest
                  ? 'Guest session'
                  : isDemo
                    ? 'Demo account'
                    : 'Trading account'}
                {isDemo
                  ? ' · Demo funds'
                  : user?.isVerified
                    ? ' · Email verified'
                    : ''}
              </p>
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
          <div
            className="notice"
            role="status"
            style={{ margin: '14px 20px 0' }}
          >
            {actionNotice}
          </div>
        )}

        {/* Modal Body */}
        <div className="profile-modal-body">
          {/* Account Details Section */}
          <div className="profile-section">
            <span
              className="eyebrow"
              style={{ marginBottom: 8, display: 'block' }}
            >
              Account details
            </span>
            <div className="profile-info-grid">
              <div className="profile-info-item">
                <span className="profile-info-label">
                  <Hash size={13} />
                  <span>Account ID</span>
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
                    {isCopied ? (
                      <Check size={14} color="var(--bid-green)" />
                    ) : (
                      <Copy size={14} />
                    )}
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
                  <span>Username</span>
                </span>
                <span className="profile-info-value font-mono">
                  @{user?.username || 'trader'}
                </span>
              </div>

              <div className="profile-info-item">
                <span className="profile-info-label">
                  <Calendar size={13} />
                  <span>Date of birth</span>
                </span>
                <span className="profile-info-value">{displayDob}</span>
              </div>
            </div>
          </div>

          {/* Portfolio & USDT Liquidity Snapshot */}
          <div className="profile-section">
            <div className="profile-section-heading">
              <span className="eyebrow">Balances</span>
              <span className="profile-subtext">
                {isLoadingBalances
                  ? 'Updating balances...'
                  : isDemo
                    ? 'Demo funds'
                    : 'Account balances'}
              </span>
            </div>

            <div className="profile-balance-card">
              <div className="profile-balance-main">
                <div className="profile-balance-tag">
                  <Wallet size={16} />
                  <span>USDT balance</span>
                </div>
                <div className="profile-balance-figure font-mono">
                  $
                  {(
                    getBalNum(usdtBalance, 'total') ||
                    getBalNum(usdtBalance, 'available')
                  ).toLocaleString('en-US', {
                    minimumFractionDigits: 2,
                    maximumFractionDigits: 2,
                  })}
                  <span className="profile-currency-label">USDT</span>
                </div>
              </div>

              <div className="profile-balance-split">
                <div>
                  <span className="profile-split-label">
                    Available for Trading
                  </span>
                  <span className="profile-split-val font-mono">
                    {getBalNum(usdtBalance, 'available').toLocaleString(
                      'en-US',
                      {
                        minimumFractionDigits: 2,
                        maximumFractionDigits: 2,
                      },
                    )}{' '}
                    USDT
                  </span>
                </div>
                <div>
                  <span className="profile-split-label">In Open Orders</span>
                  <span className="profile-split-val font-mono muted">
                    {getBalNum(usdtBalance, 'reserved').toLocaleString(
                      'en-US',
                      {
                        minimumFractionDigits: 2,
                        maximumFractionDigits: 2,
                      },
                    )}{' '}
                    USDT
                  </span>
                </div>
              </div>
            </div>
          </div>

          {/* Other Balances */}
          {balances.filter((b) => b.asset !== 'USDT' && b.asset !== 'USD')
            .length > 0 && (
            <div className="profile-section">
              <span
                className="eyebrow"
                style={{ marginBottom: 8, display: 'block' }}
              >
                Other assets
              </span>
              <div className="profile-crypto-list">
                {balances
                  .filter((b) => b.asset !== 'USDT' && b.asset !== 'USD')
                  .map((b) => (
                    <div key={b.asset} className="profile-crypto-row">
                      <span className="profile-crypto-asset">{b.asset}</span>
                      <span className="font-mono">
                        {getBalNum(b, 'available').toLocaleString('en-US', {
                          maximumFractionDigits: 4,
                        })}
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
            onClick={handleRefreshBalances}
            disabled={isLoadingBalances}
            title="Refresh account balances"
          >
            <RefreshCw size={14} />
            <span>
              {isLoadingBalances ? 'Refreshing...' : 'Refresh balances'}
            </span>
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

'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { LogIn, LogOut, UserRound } from 'lucide-react';
import { ExchangeLogo } from './ExchangeLogo';
import { ExchangeWordmark } from './ExchangeWordmark';
import { useEffect, useState } from 'react';
import { clearAuthSession, getCurrentUser } from '@/lib/auth';
import { UserRecord } from '@/types';
import { UserProfileModal } from '@/components/profile/UserProfileModal';

export function AppHeader() {
  const pathname = usePathname();
  const router = useRouter();
  const [user, setUser] = useState<UserRecord | null>(null);
  const [isProfileOpen, setIsProfileOpen] = useState(false);

  useEffect(() => {
    setUser(getCurrentUser());
  }, []);

  const displayName = user?.fullName || user?.username || '';

  return (
    <>
      <header className="app-header">
        <Link href="/terminal" className="brand" aria-label="DETE home">
          <ExchangeLogo className="brand-mark" />
          <ExchangeWordmark className="brand-wordmark" />
          <span className="brand-caption">EXCHANGE</span>
        </Link>
        <nav className="primary-nav" aria-label="Main navigation">
          {[
            ['/terminal', 'Trading'],
            ['/dashboard', 'Telemetry'],
            ['/admin/audit', 'Audit log'],
          ].map(([href, label]) => (
            <Link
              key={href}
              href={href}
              aria-current={pathname === href ? 'page' : undefined}
            >
              {label}
            </Link>
          ))}
        </nav>
        <div className="header-account">
          {user ? (
            <button
              className="account-badge-button"
              onClick={() => setIsProfileOpen(true)}
              title="Open Trader Profile & Balances"
              aria-label="Open Trader Profile & Balances"
            >
              <div className="account-avatar-mini">
                <UserRound size={13} />
              </div>
              <span className="account-text">{displayName || 'Trader'}</span>
            </button>
          ) : (
            <Link
              href="/login"
              className="account-name"
              style={{ cursor: 'pointer' }}
            >
              <UserRound size={14} />
              <span>Sign in</span>
            </Link>
          )}

          <button
            className="icon-button"
            aria-label={user ? 'Sign out' : 'Sign in'}
            title={user ? 'Sign out' : 'Sign in'}
            onClick={() => {
              if (user) {
                clearAuthSession();
                setUser(null);
                router.push('/login');
              } else {
                router.push('/login');
              }
            }}
          >
            {user ? <LogOut size={16} /> : <LogIn size={16} />}
          </button>
        </div>
      </header>

      <UserProfileModal
        isOpen={isProfileOpen}
        onClose={() => setIsProfileOpen(false)}
        onRefreshBalances={() => {
          // Trigger any balance re-fetch if needed
        }}
      />
    </>
  );
}

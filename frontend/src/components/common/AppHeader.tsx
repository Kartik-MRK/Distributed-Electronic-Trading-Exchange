'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { Activity, LogIn, LogOut, UserRound } from 'lucide-react';
import { useEffect, useState } from 'react';
import { clearAuthSession, getCurrentUser } from '@/lib/auth';

export function AppHeader() {
  const pathname = usePathname();
  const router = useRouter();
  const [username, setUsername] = useState('');
  useEffect(() => {
    setUsername(getCurrentUser()?.username ?? '');
  }, []);
  return (
    <header className="app-header">
      <Link href="/terminal" className="brand" aria-label="DETE home">
        <span className="brand-mark">
          <Activity size={19} strokeWidth={1.7} />
        </span>
        <span>
          DETE<span className="brand-caption">EXCHANGE</span>
        </span>
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
        <span className="account-name">
          <UserRound size={14} />
          <span>{username || 'Guest'}</span>
        </span>
        <button
          className="icon-button"
          aria-label={username ? 'Sign out' : 'Sign in'}
          title={username ? 'Sign out' : 'Sign in'}
          onClick={() => {
            clearAuthSession();
            router.push('/login');
          }}
        >
          {username ? <LogOut size={16} /> : <LogIn size={16} />}
        </button>
      </div>
    </header>
  );
}

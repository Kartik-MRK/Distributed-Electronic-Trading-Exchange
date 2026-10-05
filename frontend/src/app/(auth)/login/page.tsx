'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import { api } from '../../../lib/api';
import { setAuthSession, setCustomUserSession, DEMO_CREDENTIALS } from '../../../lib/auth';
import { Activity, ShieldCheck, Zap, Lock, User, ArrowRight } from 'lucide-react';
import Link from 'next/link';

export default function LoginPage() {
  const router = useRouter();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username || !password) {
      setErrorMsg('Please enter both username and password');
      return;
    }

    setIsLoading(true);
    setErrorMsg(null);

    try {
      const res = await api.auth.login({ username, password });
      setAuthSession(res);
      router.push('/terminal');
    } catch (err) {
      // In offline / standalone preview mode, allow standard auth or display error
      if (username === 'demo' && password === DEMO_CREDENTIALS.password) {
        setCustomUserSession('mock-jwt-token-demo', {
          userId: 'demo-user-id',
          username: 'demo',
          email: 'demo@dete.io',
          roles: ['ROLE_TRADER'],
        });
        router.push('/terminal');
      } else {
        setErrorMsg(err instanceof Error ? err.message : 'Invalid credentials');
      }
    } finally {
      setIsLoading(false);
    }
  };

  const handleTryDemo = async () => {
    setIsLoading(true);
    setErrorMsg(null);

    try {
      const res = await api.auth.login(DEMO_CREDENTIALS);
      setAuthSession(res);
      router.push('/terminal');
    } catch {
      // Instant standalone fallback if Gateway is not currently running locally
      setCustomUserSession('mock-jwt-token-demo', {
        userId: 'demo-user-id',
        username: 'demo',
        email: 'demo@dete.io',
        roles: ['ROLE_TRADER'],
      });
      router.push('/terminal');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        width: '100vw',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'radial-gradient(ellipse at top, #142038 0%, #090d16 70%)',
        padding: 20,
      }}
    >
      <div
        className="glass-panel"
        style={{
          maxWidth: 420,
          width: '100%',
          padding: 32,
          borderRadius: 12,
          border: '1px solid var(--border-accent)',
          boxShadow: '0 20px 50px rgba(0,0,0,0.6)',
        }}
      >
        {/* Brand Header */}
        <div style={{ textAlign: 'center', marginBottom: 28 }}>
          <div
            style={{
              width: 48,
              height: 48,
              borderRadius: 12,
              background: 'linear-gradient(135deg, #00d2ff 0%, #0066ff 100%)',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              marginBottom: 12,
              boxShadow: '0 0 20px rgba(0, 210, 255, 0.4)',
            }}
          >
            <Activity size={26} color="#ffffff" />
          </div>
          <h1 style={{ fontSize: '1.4rem', fontWeight: 800, color: 'var(--text-primary)', letterSpacing: '0.02em' }}>
            DETE TRADING TERMINAL
          </h1>
          <p style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', marginTop: 4 }}>
            Ultra-low latency distributed exchange network
          </p>
        </div>

        {/* 1-Click Demo Login Button */}
        <button
          type="button"
          onClick={handleTryDemo}
          disabled={isLoading}
          style={{
            width: '100%',
            padding: '12px 16px',
            borderRadius: 8,
            border: '1px solid rgba(0, 245, 160, 0.4)',
            background: 'linear-gradient(135deg, rgba(0, 245, 160, 0.15) 0%, rgba(0, 210, 255, 0.15) 100%)',
            color: 'var(--bid-green)',
            fontWeight: 700,
            fontSize: '0.88rem',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 8,
            marginBottom: 20,
            transition: 'all 0.2s ease',
          }}
          onMouseEnter={(e) => {
            (e.currentTarget as HTMLElement).style.background =
              'linear-gradient(135deg, rgba(0, 245, 160, 0.28) 0%, rgba(0, 210, 255, 0.28) 100%)';
            (e.currentTarget as HTMLElement).style.boxShadow = '0 0 15px rgba(0, 245, 160, 0.3)';
          }}
          onMouseLeave={(e) => {
            (e.currentTarget as HTMLElement).style.background =
              'linear-gradient(135deg, rgba(0, 245, 160, 0.15) 0%, rgba(0, 210, 255, 0.15) 100%)';
            (e.currentTarget as HTMLElement).style.boxShadow = 'none';
          }}
        >
          <Zap size={18} color="var(--bid-green)" />
          <span>⚡ Try Demo (1-Click Instant Access)</span>
        </button>

        {/* Divider */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            marginBottom: 20,
            color: 'var(--text-muted)',
            fontSize: '0.72rem',
          }}
        >
          <div style={{ flex: 1, height: 1, backgroundColor: 'var(--border-subtle)' }} />
          <span>OR SIGN IN WITH ACCOUNT</span>
          <div style={{ flex: 1, height: 1, backgroundColor: 'var(--border-subtle)' }} />
        </div>

        {/* Error Alert */}
        {errorMsg && (
          <div
            style={{
              padding: '8px 12px',
              backgroundColor: 'rgba(255, 59, 105, 0.12)',
              border: '1px solid rgba(255, 59, 105, 0.3)',
              borderRadius: 6,
              color: 'var(--ask-red)',
              fontSize: '0.78rem',
              marginBottom: 16,
            }}
          >
            {errorMsg}
          </div>
        )}

        {/* Login Form */}
        <form onSubmit={handleLogin} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div>
            <label style={{ display: 'block', fontSize: '0.72rem', color: 'var(--text-secondary)', marginBottom: 6 }}>
              USERNAME / EMAIL
            </label>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                backgroundColor: 'var(--bg-input)',
                border: '1px solid var(--border-subtle)',
                borderRadius: 6,
                padding: '9px 12px',
              }}
            >
              <User size={16} color="var(--text-muted)" />
              <input
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="demo"
                style={{
                  flex: 1,
                  background: 'none',
                  border: 'none',
                  color: 'var(--text-primary)',
                  fontSize: '0.85rem',
                  outline: 'none',
                }}
              />
            </div>
          </div>

          <div>
            <label style={{ display: 'block', fontSize: '0.72rem', color: 'var(--text-secondary)', marginBottom: 6 }}>
              PASSWORD
            </label>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                backgroundColor: 'var(--bg-input)',
                border: '1px solid var(--border-subtle)',
                borderRadius: 6,
                padding: '9px 12px',
              }}
            >
              <Lock size={16} color="var(--text-muted)" />
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••••••"
                style={{
                  flex: 1,
                  background: 'none',
                  border: 'none',
                  color: 'var(--text-primary)',
                  fontSize: '0.85rem',
                  outline: 'none',
                }}
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={isLoading}
            style={{
              marginTop: 6,
              padding: '11px 0',
              borderRadius: 6,
              background: 'linear-gradient(135deg, #00d2ff 0%, #0077ff 100%)',
              color: '#ffffff',
              fontWeight: 700,
              fontSize: '0.88rem',
              border: 'none',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 6,
              transition: 'opacity 0.15s ease',
            }}
          >
            <span>{isLoading ? 'AUTHENTICATING...' : 'SIGN IN'}</span>
            <ArrowRight size={16} />
          </button>
        </form>

        {/* Footer Link */}
        <div style={{ textAlign: 'center', marginTop: 24, fontSize: '0.76rem', color: 'var(--text-muted)' }}>
          Don&apos;t have an account?{' '}
          <Link href="/register" style={{ color: 'var(--accent-cyan)', fontWeight: 600 }}>
            Register new trader account
          </Link>
        </div>
      </div>
    </div>
  );
}

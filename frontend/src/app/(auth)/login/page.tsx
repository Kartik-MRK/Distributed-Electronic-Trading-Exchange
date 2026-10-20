'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import { api } from '../../../lib/api';
import {
  setAuthSession,
  setCustomUserSession,
  DEMO_CREDENTIALS,
} from '../../../lib/auth';
import { ArrowRight } from 'lucide-react';
import { AuthLayout } from '@/components/common/AuthLayout';
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
    <AuthLayout>
      <h1>Welcome back</h1>
      <p>Sign in to your trading workspace.</p>
      {errorMsg && (
        <div className="notice error" role="alert">
          {errorMsg}
        </div>
      )}
      <form className="auth-form" onSubmit={handleLogin}>
        <div className="field">
          <label htmlFor="username">Username</label>
          <input
            id="username"
            name="username"
            autoComplete="username"
            required
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="Enter your username"
          />
        </div>
        <div className="field">
          <label htmlFor="password">Password</label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="Enter your password"
          />
        </div>
        <button className="button primary" disabled={isLoading}>
          {isLoading ? 'Signing in...' : 'Sign in'}
          <ArrowRight size={15} />
        </button>
      </form>
      <div className="auth-divider">or explore first</div>
      <button
        className="button full-width"
        onClick={handleTryDemo}
        disabled={isLoading}
      >
        Open demo workspace
        <ArrowRight size={15} />
      </button>
      <div className="auth-footer">
        New to DETE? <Link href="/register">Create an account</Link>
      </div>
    </AuthLayout>
  );
}

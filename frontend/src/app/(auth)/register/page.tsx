'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import { api } from '../../../lib/api';
import { ArrowRight } from 'lucide-react';
import { AuthLayout } from '@/components/common/AuthLayout';
import Link from 'next/link';

export default function RegisterPage() {
  const router = useRouter();
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username || !email || !password) {
      setErrorMsg('Please complete all fields');
      return;
    }
    if (password !== confirmPassword) {
      setErrorMsg('Passwords do not match');
      return;
    }
    if (password.length < 8) {
      setErrorMsg('Password must be at least 8 characters long');
      return;
    }

    setIsLoading(true);
    setErrorMsg(null);

    try {
      await api.auth.register({ username, email, password });
      setSuccessMsg(
        'Account registered successfully! Redirecting to sign in...',
      );
      setTimeout(() => {
        router.push('/login');
      }, 1500);
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : 'Registration failed');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <AuthLayout>
      <h1>Create your account</h1>
      <p>Set up your profile to access the exchange.</p>
      {errorMsg && (
        <div className="notice error" role="alert">
          {errorMsg}
        </div>
      )}
      {successMsg && (
        <div className="notice" role="status">
          {successMsg}
        </div>
      )}
      <form className="auth-form" onSubmit={handleRegister}>
        <div className="field">
          <label htmlFor="username">Username</label>
          <input
            id="username"
            autoComplete="username"
            required
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="Choose a username"
          />
        </div>
        <div className="field">
          <label htmlFor="email">Email address</label>
          <input
            id="email"
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="you@example.com"
          />
        </div>
        <div className="field">
          <label htmlFor="password">
            Password <span>At least 8 characters</span>
          </label>
          <input
            id="password"
            type="password"
            autoComplete="new-password"
            required
            minLength={8}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="Create a password"
          />
        </div>
        <div className="field">
          <label htmlFor="confirm-password">Confirm password</label>
          <input
            id="confirm-password"
            type="password"
            autoComplete="new-password"
            required
            minLength={8}
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            placeholder="Repeat your password"
          />
        </div>
        <button className="button primary" disabled={isLoading || !!successMsg}>
          {isLoading ? 'Creating account...' : 'Create account'}
          <ArrowRight size={15} />
        </button>
      </form>
      <div className="auth-footer">
        Already have an account? <Link href="/login">Sign in</Link>
      </div>
    </AuthLayout>
  );
}

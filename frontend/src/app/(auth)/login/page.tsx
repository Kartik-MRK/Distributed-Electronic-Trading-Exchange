'use client';

import React, { useState, useEffect, useRef, Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { api } from '../../../lib/api';
import {
  setAuthSession,
  setCustomUserSession,
  DEMO_CREDENTIALS,
} from '../../../lib/auth';
import {
  ArrowRight,
  Sparkles,
  ShieldCheck,
  Mail,
  User,
  Lock,
  Calendar,
  CheckCircle2,
  RefreshCw,
  ArrowLeft,
  KeyRound,
} from 'lucide-react';
import { AuthLayout } from '@/components/common/AuthLayout';
import { UserRecord } from '@/types';

function createFallbackGuestUser(): UserRecord {
  const seed = Date.now().toString(36);
  return {
    userId: `guest-uuid-${seed}`,
    username: `guest_${seed}`,
    email: `guest_${seed}@guest.dete.io`,
    roles: ['ROLE_GUEST'],
    fullName: 'Guest Trader',
    role: 'ROLE_GUEST',
    isVerified: false,
    isDemo: true,
  };
}

export default function LoginPage() {
  return (
    <Suspense fallback={<div className="loading-screen">Loading DETE Access...</div>}>
      <AuthContent />
    </Suspense>
  );
}

function AuthContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const initialTab = searchParams.get('tab') === 'signup' ? 'signup' : 'signin';
  const returnUrl = searchParams.get('returnUrl') || '/terminal';

  const [activeTab, setActiveTab] = useState<'signin' | 'signup'>(initialTab);
  const [signupStep, setSignupStep] = useState<'form' | 'otp'>('form');

  // Sign In State
  const [loginIdentifier, setLoginIdentifier] = useState('');
  const [loginPassword, setLoginPassword] = useState('');

  // Sign Up State
  const [fullName, setFullName] = useState('');
  const [dob, setDob] = useState('');
  const [email, setEmail] = useState('');
  const [signupPassword, setSignupPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  // OTP State
  const [otpValues, setOtpValues] = useState<string[]>(['', '', '', '', '', '']);
  const [otpNotice, setOtpNotice] = useState<string | null>(null);
  const [countdown, setCountdown] = useState(60);
  const [isResending, setIsResending] = useState(false);
  const otpInputsRef = useRef<(HTMLInputElement | null)[]>([]);

  // Feedback State
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  // Countdown timer effect for OTP resend
  useEffect(() => {
    let timer: NodeJS.Timeout;
    if (signupStep === 'otp' && countdown > 0) {
      timer = setInterval(() => {
        setCountdown((c) => Math.max(0, c - 1));
      }, 1000);
    }
    return () => clearInterval(timer);
  }, [signupStep, countdown]);

  const handleTabChange = (tab: 'signin' | 'signup') => {
    setActiveTab(tab);
    setErrorMsg(null);
    setSuccessMsg(null);
    if (tab === 'signin') {
      setSignupStep('form');
    }
  };

  /* --------------------------------------------------------------------------
     1. Returning User Sign In
     -------------------------------------------------------------------------- */
  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!loginIdentifier || !loginPassword) {
      setErrorMsg('Please enter your email or username and password');
      return;
    }

    setIsLoading(true);
    setErrorMsg(null);

    try {
      const res = await api.auth.login({
        identifier: loginIdentifier,
        password: loginPassword,
      });
      setAuthSession(res);
      router.push(returnUrl);
    } catch (err) {
      // In offline / standalone preview mode, allow demo login fallback
      if (
        (loginIdentifier === 'demo' || loginIdentifier === 'demo@dete.io') &&
        loginPassword === DEMO_CREDENTIALS.password
      ) {
        setCustomUserSession('mock-jwt-token-demo', {
          userId: 'demo-user-id',
          username: 'demo',
          email: 'demo@dete.io',
          roles: ['ROLE_TRADER'],
          fullName: 'Demo Trader',
          role: 'ROLE_TRADER',
          isVerified: true,
          isDemo: true,
        });
        router.push(returnUrl);
      } else {
        setErrorMsg(err instanceof Error ? err.message : 'Invalid credentials. Please verify your email and password.');
      }
    } finally {
      setIsLoading(false);
    }
  };

  /* --------------------------------------------------------------------------
     2. 1-Click Guest Trader Login (50,000 USDT Demo Balance)
     -------------------------------------------------------------------------- */
  const handleGuestLogin = async () => {
    setIsLoading(true);
    setErrorMsg(null);

    try {
      const res = await api.auth.guestLogin({
        nickname: 'Guest Trader',
      });
      setAuthSession(res);
      router.push(returnUrl);
    } catch {
      // Instant standalone fallback if Gateway is not currently reachable locally
      const fallbackGuest = createFallbackGuestUser();
      setCustomUserSession('mock-jwt-guest-token', fallbackGuest);
      router.push(returnUrl);
    } finally {
      setIsLoading(false);
    }
  };

  /* --------------------------------------------------------------------------
     3. Initiate Registration (Sends Email OTP)
     -------------------------------------------------------------------------- */
  const handleInitiateSignup = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);

    if (!fullName || !email || !signupPassword) {
      setErrorMsg('Please fill in all required fields');
      return;
    }
    if (signupPassword !== confirmPassword) {
      setErrorMsg('Passwords do not match');
      return;
    }
    if (signupPassword.length < 8) {
      setErrorMsg('Password must be at least 8 characters long');
      return;
    }

    setIsLoading(true);

    try {
      const res = await api.auth.initiateRegister({
        fullName,
        dateOfBirth: dob || undefined,
        email,
        password: signupPassword,
      });

      setOtpNotice(res.message || `Verification code sent to ${email}`);
      setCountdown(res.expiresInSeconds ? Math.min(res.expiresInSeconds, 60) : 60);
      setOtpValues(['', '', '', '', '', '']);
      setSignupStep('otp');
      // Focus first OTP input
      setTimeout(() => {
        otpInputsRef.current[0]?.focus();
      }, 150);
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : 'Registration initiation failed');
    } finally {
      setIsLoading(false);
    }
  };

  /* --------------------------------------------------------------------------
     4. Resend OTP
     -------------------------------------------------------------------------- */
  const handleResendOtp = async () => {
    if (countdown > 0 || isResending) return;
    setIsResending(true);
    setErrorMsg(null);

    try {
      const res = await api.auth.initiateRegister({
        fullName,
        dateOfBirth: dob || undefined,
        email,
        password: signupPassword,
      });
      setOtpNotice(res.message || `New code sent to ${email}`);
      setCountdown(60);
      setOtpValues(['', '', '', '', '', '']);
      otpInputsRef.current[0]?.focus();
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : 'Failed to resend OTP code');
    } finally {
      setIsResending(false);
    }
  };

  /* --------------------------------------------------------------------------
     5. Verify OTP & Finalize Registration
     -------------------------------------------------------------------------- */
  const handleVerifyOtp = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    const otpCode = otpValues.join('');
    if (otpCode.length !== 6) {
      setErrorMsg('Please enter the complete 6-digit verification code');
      return;
    }

    setIsLoading(true);
    setErrorMsg(null);

    try {
      const res = await api.auth.verifyOtp({
        email,
        otp: otpCode,
      });

      setSuccessMsg('Email verified! Pre-funding 50,000 USDT to your account...');
      setAuthSession(res);

      setTimeout(() => {
        router.push(returnUrl);
      }, 1200);
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : 'Invalid or expired OTP code');
    } finally {
      setIsLoading(false);
    }
  };

  /* --------------------------------------------------------------------------
     OTP Input Handlers (Segmented auto-advancing boxes)
     -------------------------------------------------------------------------- */
  const handleOtpChange = (index: number, val: string) => {
    // Only accept numeric digits
    const cleaned = val.replace(/\D/g, '');
    if (!cleaned) {
      const newOtp = [...otpValues];
      newOtp[index] = '';
      setOtpValues(newOtp);
      return;
    }

    const lastChar = cleaned.slice(-1);
    const newOtp = [...otpValues];
    newOtp[index] = lastChar;
    setOtpValues(newOtp);

    // Auto-advance to next input
    if (index < 5) {
      otpInputsRef.current[index + 1]?.focus();
    }

    // Auto-submit if all 6 digits entered
    if (index === 5 || newOtp.every((d) => d.length === 1)) {
      if (newOtp.join('').length === 6) {
        setTimeout(() => {
          handleVerifyOtp();
        }, 100);
      }
    }
  };

  const handleOtpKeyDown = (index: number, e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Backspace') {
      if (!otpValues[index] && index > 0) {
        otpInputsRef.current[index - 1]?.focus();
      }
    } else if (e.key === 'ArrowLeft' && index > 0) {
      otpInputsRef.current[index - 1]?.focus();
    } else if (e.key === 'ArrowRight' && index < 5) {
      otpInputsRef.current[index + 1]?.focus();
    }
  };

  const handleOtpPaste = (e: React.ClipboardEvent<HTMLInputElement>) => {
    e.preventDefault();
    const pasted = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, 6);
    if (!pasted) return;

    const newOtp = [...otpValues];
    for (let i = 0; i < pasted.length; i++) {
      newOtp[i] = pasted[i];
    }
    setOtpValues(newOtp);

    const nextIndex = Math.min(pasted.length, 5);
    otpInputsRef.current[nextIndex]?.focus();

    if (pasted.length === 6) {
      setTimeout(() => {
        handleVerifyOtp();
      }, 100);
    }
  };

  return (
    <AuthLayout>
      {/* Tab Navigation Header */}
      <div className="auth-tabs" role="tablist">
        <button
          role="tab"
          aria-selected={activeTab === 'signin'}
          className={`auth-tab-btn ${activeTab === 'signin' ? 'active' : ''}`}
          onClick={() => handleTabChange('signin')}
        >
          Sign In
        </button>
        <button
          role="tab"
          aria-selected={activeTab === 'signup'}
          className={`auth-tab-btn ${activeTab === 'signup' ? 'active' : ''}`}
          onClick={() => handleTabChange('signup')}
        >
          Create Account
        </button>
      </div>

      {/* Global Alerts */}
      {errorMsg && (
        <div className="notice error" role="alert" style={{ marginBottom: 16 }}>
          {errorMsg}
        </div>
      )}
      {successMsg && (
        <div className="notice" role="status" style={{ marginBottom: 16, borderColor: 'var(--bid-green)' }}>
          <CheckCircle2 size={16} color="var(--bid-green)" style={{ marginRight: 6 }} />
          <span>{successMsg}</span>
        </div>
      )}

      {/* ====================================================================
          TAB 1: SIGN IN (Returning User)
          ==================================================================== */}
      {activeTab === 'signin' && (
        <div>
          <div className="auth-header-copy">
            <h1>Access Exchange</h1>
            <p>Enter your credentials to enter the DETE trading terminal.</p>
          </div>

          <form className="auth-form" onSubmit={handleLogin}>
            <div className="field">
              <label htmlFor="loginIdentifier">
                <span className="field-icon-label"><User size={13} /> Email or Username</span>
              </label>
              <input
                id="loginIdentifier"
                name="loginIdentifier"
                autoComplete="username"
                required
                value={loginIdentifier}
                onChange={(e) => setLoginIdentifier(e.target.value)}
                placeholder="trader@domain.com or username"
              />
            </div>

            <div className="field">
              <label htmlFor="loginPassword">
                <span className="field-icon-label"><Lock size={13} /> Password</span>
              </label>
              <input
                id="loginPassword"
                name="loginPassword"
                type="password"
                autoComplete="current-password"
                required
                value={loginPassword}
                onChange={(e) => setLoginPassword(e.target.value)}
                placeholder="Enter account password"
              />
            </div>

            <button className="button primary full-width auth-submit-btn" disabled={isLoading}>
              {isLoading ? 'Authenticating...' : 'Sign In to Terminal'}
              <ArrowRight size={15} />
            </button>
          </form>

          {/* Quick 1-Click Guest Trader Entry */}
          <div className="auth-divider">instant trial</div>

          <button
            className="guest-action-card"
            onClick={handleGuestLogin}
            disabled={isLoading}
            type="button"
            title="Start instant paper trading with 50,000 USDT pre-funded"
          >
            <div className="guest-action-icon">
              <Sparkles size={18} color="var(--accent-amber)" />
            </div>
            <div className="guest-action-text">
              <strong>1-Click Guest Access</strong>
              <span>Instant terminal entry • Pre-funded 50,000 USDT</span>
            </div>
            <ArrowRight size={16} className="guest-arrow" />
          </button>
        </div>
      )}

      {/* ====================================================================
          TAB 2: SIGN UP (Email OTP Verification Flow)
          ==================================================================== */}
      {activeTab === 'signup' && signupStep === 'form' && (
        <div>
          <div className="auth-header-copy">
            <h1>Create Trader Account</h1>
            <p>Sign up to receive 50,000 USDT initial exchange liquidity.</p>
          </div>

          <form className="auth-form" onSubmit={handleInitiateSignup}>
            <div className="field">
              <label htmlFor="fullName">
                <span className="field-icon-label"><User size={13} /> Full Legal Name</span>
              </label>
              <input
                id="fullName"
                name="fullName"
                autoComplete="name"
                required
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                placeholder="e.g. Satoshi Nakamoto"
              />
            </div>

            <div className="field">
              <label htmlFor="dob">
                <span className="field-icon-label"><Calendar size={13} /> Date of Birth</span>
              </label>
              <input
                id="dob"
                name="dob"
                type="date"
                autoComplete="bday"
                value={dob}
                onChange={(e) => setDob(e.target.value)}
                placeholder="YYYY-MM-DD"
              />
            </div>

            <div className="field">
              <label htmlFor="email">
                <span className="field-icon-label"><Mail size={13} /> Email Address</span>
              </label>
              <input
                id="email"
                name="email"
                type="email"
                autoComplete="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="trader@exchange.com"
              />
            </div>

            <div className="field">
              <label htmlFor="signupPassword">
                <span className="field-icon-label"><Lock size={13} /> Password</span>
                <span className="muted font-mono" style={{ fontSize: 11 }}>Min 8 chars</span>
              </label>
              <input
                id="signupPassword"
                name="signupPassword"
                type="password"
                autoComplete="new-password"
                required
                minLength={8}
                value={signupPassword}
                onChange={(e) => setSignupPassword(e.target.value)}
                placeholder="Create secure password"
              />
            </div>

            <div className="field">
              <label htmlFor="confirmPassword">
                <span className="field-icon-label"><ShieldCheck size={13} /> Confirm Password</span>
              </label>
              <input
                id="confirmPassword"
                name="confirmPassword"
                type="password"
                autoComplete="new-password"
                required
                minLength={8}
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                placeholder="Repeat secure password"
              />
            </div>

            <button className="button primary full-width auth-submit-btn" disabled={isLoading}>
              {isLoading ? 'Verifying & Sending OTP...' : 'Send Verification OTP'}
              <ArrowRight size={15} />
            </button>
          </form>

          {/* Quick 1-Click Guest Trader Entry */}
          <div className="auth-divider">or skip registration</div>

          <button
            className="guest-action-card"
            onClick={handleGuestLogin}
            disabled={isLoading}
            type="button"
          >
            <div className="guest-action-icon">
              <Sparkles size={18} color="var(--accent-amber)" />
            </div>
            <div className="guest-action-text">
              <strong>Continue as Guest Trader</strong>
              <span>Immediate access without verification</span>
            </div>
            <ArrowRight size={16} className="guest-arrow" />
          </button>
        </div>
      )}

      {/* ====================================================================
          TAB 2 STEP B: OTP VERIFICATION SCREEN
          ==================================================================== */}
      {activeTab === 'signup' && signupStep === 'otp' && (
        <div className="otp-container">
          <button
            className="otp-back-btn"
            onClick={() => setSignupStep('form')}
            type="button"
            title="Edit registration details"
          >
            <ArrowLeft size={14} />
            <span>Change Details / Email</span>
          </button>

          <div className="auth-header-copy">
            <div className="otp-icon-header">
              <KeyRound size={22} color="var(--accent-cyan)" />
            </div>
            <h1>Verify Your Email</h1>
            <p>
              We dispatched a 6-digit verification code to <strong style={{ color: 'var(--text-primary)' }}>{email}</strong>.
            </p>
          </div>

          {otpNotice && (
            <div className="notice" role="status" style={{ marginBottom: 18 }}>
              {otpNotice}
            </div>
          )}


          <form onSubmit={handleVerifyOtp} className="auth-form">
            <div className="otp-grid">
              {otpValues.map((digit, idx) => (
                <input
                  key={idx}
                  ref={(el) => {
                    otpInputsRef.current[idx] = el;
                  }}
                  type="text"
                  inputMode="numeric"
                  maxLength={1}
                  className="otp-digit-box font-mono"
                  value={digit}
                  onChange={(e) => handleOtpChange(idx, e.target.value)}
                  onKeyDown={(e) => handleOtpKeyDown(idx, e)}
                  onPaste={idx === 0 ? handleOtpPaste : undefined}
                  autoFocus={idx === 0}
                  aria-label={`Digit ${idx + 1} of verification code`}
                />
              ))}
            </div>

            <button
              className="button primary full-width auth-submit-btn"
              disabled={isLoading || otpValues.join('').length !== 6}
            >
              {isLoading ? 'Verifying Code...' : 'Verify & Open Terminal'}
              <ArrowRight size={15} />
            </button>
          </form>

          <div className="otp-resend-row">
            {countdown > 0 ? (
              <span className="otp-timer-text">
                Resend code in <strong className="font-mono">{countdown}s</strong>
              </span>
            ) : (
              <button
                className="otp-resend-btn"
                onClick={handleResendOtp}
                disabled={isResending}
                type="button"
              >
                <RefreshCw size={13} className={isResending ? 'spin' : ''} />
                <span>{isResending ? 'Sending...' : 'Resend Verification Code'}</span>
              </button>
            )}
          </div>
        </div>
      )}
    </AuthLayout>
  );
}

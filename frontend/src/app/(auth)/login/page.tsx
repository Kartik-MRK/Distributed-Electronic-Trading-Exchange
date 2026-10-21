'use client';

import React, { useState, useEffect, useRef, Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { api } from '../../../lib/api';
import {
  setAuthSession,
  setCustomUserSession,
  DEMO_CREDENTIALS,
} from '../../../lib/auth';
import { ArrowRight, CheckCircle2, RefreshCw, ArrowLeft } from 'lucide-react';
import { AuthLayout } from '@/components/common/AuthLayout';
import { Loader } from '@/components/common/Loader';
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
    <AuthLayout>
      <Suspense fallback={<Loader label="Loading exchange access..." />}>
        <AuthContent />
      </Suspense>
    </AuthLayout>
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
  const [otpValues, setOtpValues] = useState<string[]>([
    '',
    '',
    '',
    '',
    '',
    '',
  ]);
  const [otpNotice, setOtpNotice] = useState<string | null>(null);
  const [countdown, setCountdown] = useState(60);
  const [isResending, setIsResending] = useState(false);
  const otpInputsRef = useRef<(HTMLInputElement | null)[]>([]);

  // Feedback State
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [pendingAction, setPendingAction] = useState<
    'signin' | 'guest' | 'signup' | 'verify' | null
  >(null);
  const isLoading = pendingAction !== null;

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

    setPendingAction('signin');
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
        setErrorMsg(
          err instanceof Error
            ? err.message
            : 'Invalid credentials. Please verify your email and password.',
        );
      }
    } finally {
      setPendingAction(null);
    }
  };

  /* --------------------------------------------------------------------------
     2. 1-Click Guest Trader Login (50,000 USDT Demo Balance)
     -------------------------------------------------------------------------- */
  const handleGuestLogin = async () => {
    setPendingAction('guest');
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
      setPendingAction(null);
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

    setPendingAction('signup');

    try {
      const res = await api.auth.initiateRegister({
        fullName,
        dateOfBirth: dob || undefined,
        email,
        password: signupPassword,
      });

      setOtpNotice(res.message || `Verification code sent to ${email}`);
      setCountdown(
        res.expiresInSeconds ? Math.min(res.expiresInSeconds, 60) : 60,
      );
      setOtpValues(['', '', '', '', '', '']);
      setSignupStep('otp');
      // Focus first OTP input
      setTimeout(() => {
        otpInputsRef.current[0]?.focus();
      }, 150);
    } catch (err) {
      setErrorMsg(
        err instanceof Error ? err.message : 'Registration initiation failed',
      );
    } finally {
      setPendingAction(null);
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
      setErrorMsg(
        err instanceof Error ? err.message : 'Failed to resend OTP code',
      );
    } finally {
      setIsResending(false);
    }
  };

  /* --------------------------------------------------------------------------
     5. Verify OTP & Finalize Registration
     -------------------------------------------------------------------------- */
  const handleVerifyOtp = async (e?: React.FormEvent, code?: string) => {
    if (e) e.preventDefault();
    const otpCode = code ?? otpValues.join('');
    if (otpCode.length !== 6) {
      setErrorMsg('Please enter the complete 6-digit verification code');
      return;
    }

    setPendingAction('verify');
    setErrorMsg(null);

    try {
      const res = await api.auth.verifyOtp({
        email,
        otp: otpCode,
      });

      setSuccessMsg('Email verified. Opening your workspace...');
      setAuthSession(res);

      setTimeout(() => {
        router.push(returnUrl);
      }, 1200);
    } catch (err) {
      setErrorMsg(
        err instanceof Error ? err.message : 'Invalid or expired OTP code',
      );
    } finally {
      setPendingAction(null);
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
          handleVerifyOtp(undefined, newOtp.join(''));
        }, 100);
      }
    }
  };

  const handleOtpKeyDown = (
    index: number,
    e: React.KeyboardEvent<HTMLInputElement>,
  ) => {
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
    const pasted = e.clipboardData
      .getData('text')
      .replace(/\D/g, '')
      .slice(0, 6);
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
        handleVerifyOtp(undefined, newOtp.join(''));
      }, 100);
    }
  };

  return (
    <>
      {/* Tab Navigation Header */}
      <div
        className="auth-tabs"
        role="tablist"
        aria-label="Account access"
        onKeyDown={(event) => {
          if (
            !['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key) ||
            isLoading
          )
            return;
          event.preventDefault();
          const next =
            event.key === 'Home'
              ? 'signin'
              : event.key === 'End'
                ? 'signup'
                : activeTab === 'signin'
                  ? 'signup'
                  : 'signin';
          handleTabChange(next);
          document.getElementById(`tab-${next}`)?.focus();
        }}
      >
        <button
          role="tab"
          aria-selected={activeTab === 'signin'}
          id="tab-signin"
          aria-controls="panel-signin"
          tabIndex={activeTab === 'signin' ? 0 : -1}
          disabled={isLoading}
          className={`auth-tab-btn ${activeTab === 'signin' ? 'active' : ''}`}
          onClick={() => handleTabChange('signin')}
        >
          Sign In
        </button>
        <button
          role="tab"
          aria-selected={activeTab === 'signup'}
          id="tab-signup"
          aria-controls="panel-signup"
          tabIndex={activeTab === 'signup' ? 0 : -1}
          disabled={isLoading}
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
        <div
          className="notice"
          role="status"
          style={{ marginBottom: 16, borderColor: 'var(--bid-green)' }}
        >
          <CheckCircle2
            size={16}
            color="var(--bid-green)"
            style={{ marginRight: 6 }}
          />
          <span>{successMsg}</span>
        </div>
      )}

      {/* ====================================================================
          TAB 1: SIGN IN (Returning User)
          ==================================================================== */}
      {activeTab === 'signin' && (
        <div
          className="auth-panel"
          role="tabpanel"
          id="panel-signin"
          aria-labelledby="tab-signin"
        >
          <div className="auth-header-copy">
            <h1>Welcome back.</h1>
            <p>Sign in to manage your account and trading activity.</p>
          </div>

          <form className="auth-form" onSubmit={handleLogin}>
            <div className="field">
              <label htmlFor="loginIdentifier">
                <span className="field-icon-label">Email or Username</span>
              </label>
              <input
                id="loginIdentifier"
                name="loginIdentifier"
                autoComplete="username"
                required
                value={loginIdentifier}
                onChange={(e) => setLoginIdentifier(e.target.value)}
                placeholder="Email or username"
              />
            </div>

            <div className="field">
              <label htmlFor="loginPassword">
                <span className="field-icon-label">Password</span>
              </label>
              <input
                id="loginPassword"
                name="loginPassword"
                type="password"
                autoComplete="current-password"
                required
                value={loginPassword}
                onChange={(e) => setLoginPassword(e.target.value)}
                placeholder="Your password"
              />
            </div>

            <button
              className="button primary full-width auth-submit-btn"
              disabled={isLoading}
              aria-busy={pendingAction === 'signin'}
            >
              {pendingAction === 'signin' ? 'Signing in...' : 'Sign In'}
              {pendingAction === 'signin' ? (
                <Loader variant="inline" label="" />
              ) : (
                <ArrowRight size={15} />
              )}
            </button>
          </form>

          {/* Quick 1-Click Guest Trader Entry */}
          <div className="auth-divider">or explore the exchange</div>

          <button
            className="guest-action-card"
            onClick={handleGuestLogin}
            disabled={isLoading}
            type="button"
            title="Start instant paper trading with 50,000 USDT pre-funded"
            aria-busy={pendingAction === 'guest'}
          >
            <div className="guest-action-text">
              <strong>
                {pendingAction === 'guest'
                  ? 'Opening workspace...'
                  : 'Continue as guest'}
              </strong>
              <span>Practice with 50,000 USDT in demo funds</span>
            </div>
            {pendingAction === 'guest' ? (
              <Loader variant="inline" label="" />
            ) : (
              <ArrowRight size={16} className="guest-arrow" />
            )}
          </button>
        </div>
      )}

      {/* ====================================================================
          TAB 2: SIGN UP (Email OTP Verification Flow)
          ==================================================================== */}
      {activeTab === 'signup' && signupStep === 'form' && (
        <div
          className="auth-panel"
          role="tabpanel"
          id="panel-signup"
          aria-labelledby="tab-signup"
        >
          <div className="auth-header-copy">
            <h1>Create your account.</h1>
            <p>
              Start with 50,000 USDT in demo funds. Verify your email to
              continue.
            </p>
          </div>

          <form className="auth-form" onSubmit={handleInitiateSignup}>
            <div className="field">
              <label htmlFor="fullName">
                <span className="field-icon-label">Full name</span>
              </label>
              <input
                id="fullName"
                name="fullName"
                autoComplete="name"
                required
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                placeholder="Your full name"
              />
            </div>

            <div className="field">
              <label htmlFor="dob">
                <span className="field-icon-label">
                  Date of birth (optional)
                </span>
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
                <span className="field-icon-label">Email Address</span>
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
                <span className="field-icon-label">Password</span>
                <span className="muted font-mono" style={{ fontSize: 11 }}>
                  Min 8 chars
                </span>
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
                <span className="field-icon-label">Confirm Password</span>
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

            <button
              className="button primary full-width auth-submit-btn"
              disabled={isLoading}
              aria-busy={pendingAction === 'signup'}
            >
              {pendingAction === 'signup'
                ? 'Sending code...'
                : 'Continue with email'}
              {pendingAction === 'signup' ? (
                <Loader variant="inline" label="" />
              ) : (
                <ArrowRight size={15} />
              )}
            </button>
          </form>

          {/* Quick 1-Click Guest Trader Entry */}
          <div className="auth-divider">or explore the exchange</div>

          <button
            className="guest-action-card"
            onClick={handleGuestLogin}
            disabled={isLoading}
            type="button"
            aria-busy={pendingAction === 'guest'}
          >
            <div className="guest-action-text">
              <strong>
                {pendingAction === 'guest'
                  ? 'Opening workspace...'
                  : 'Continue as guest'}
              </strong>
              <span>Practice with demo funds. No account required.</span>
            </div>
            {pendingAction === 'guest' ? (
              <Loader variant="inline" label="" />
            ) : (
              <ArrowRight size={16} className="guest-arrow" />
            )}
          </button>
        </div>
      )}

      {/* ====================================================================
          TAB 2 STEP B: OTP VERIFICATION SCREEN
          ==================================================================== */}
      {activeTab === 'signup' && signupStep === 'otp' && (
        <div
          className="otp-container auth-panel"
          role="tabpanel"
          id="panel-signup"
          aria-labelledby="tab-signup"
        >
          <button
            className="otp-back-btn"
            onClick={() => setSignupStep('form')}
            type="button"
            title="Edit registration details"
          >
            <ArrowLeft size={14} />
            <span>Edit email and details</span>
          </button>

          <div className="auth-header-copy">
            <h1>Check your email.</h1>
            <p>
              Enter the 6-digit code sent to{' '}
              <strong style={{ color: 'var(--text-primary)' }}>{email}</strong>.
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
              aria-busy={pendingAction === 'verify'}
            >
              {pendingAction === 'verify'
                ? 'Verifying code...'
                : 'Verify & Open Terminal'}
              {pendingAction === 'verify' ? (
                <Loader variant="inline" label="" />
              ) : (
                <ArrowRight size={15} />
              )}
            </button>
          </form>

          <div className="otp-resend-row">
            {countdown > 0 ? (
              <span className="otp-timer-text">
                Resend code in{' '}
                <strong className="font-mono">{countdown}s</strong>
              </span>
            ) : (
              <button
                className="otp-resend-btn"
                onClick={handleResendOtp}
                disabled={isResending}
                type="button"
                aria-busy={isResending}
              >
                {isResending ? (
                  <Loader variant="inline" label="" />
                ) : (
                  <RefreshCw size={13} />
                )}
                <span>{isResending ? 'Sending...' : 'Resend code'}</span>
              </button>
            )}
          </div>
        </div>
      )}
    </>
  );
}

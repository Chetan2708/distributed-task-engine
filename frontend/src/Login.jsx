
import { useState, useRef, useEffect } from 'react';
import {
  Eye,
  EyeOff,
  LogIn,
  UserPlus,
  Loader2,
  ArrowRight
} from 'lucide-react';

export default function Login({ onLogin }) {
  const [isSignUp, setIsSignUp] = useState(false);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [shake, setShake] = useState(false);

  const usernameRef = useRef(null);

  useEffect(() => {
    usernameRef.current?.focus();
  }, [isSignUp]);

  const triggerShake = () => {
    setShake(true);
    setTimeout(() => setShake(false), 500);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (!username.trim() || !password.trim()) {
      setError('Please fill in all fields');
      triggerShake();
      return;
    }

    if (isSignUp && password !== confirmPassword) {
      setError('Passwords do not match');
      triggerShake();
      return;
    }

    if (isSignUp && password.length < 8) {
      setError('Password must be at least 8 characters');
      triggerShake();
      return;
    }

    if (password.length > 72) {
      setError('Password cannot exceed 72 characters');
      triggerShake();
      return;
    }

    if (
      isSignUp &&
      (username.trim().length < 3 ||
        username.trim().length > 50)
    ) {
      setError('Username must be between 3 and 50 characters');
      triggerShake();
      return;
    }

    setLoading(true);

    try {
      const BASE_URL =
        import.meta.env.VITE_API_URL || '/api/v1';

      const endpoint = isSignUp
        ? '/auth/register'
        : '/auth/login';

      const response = await fetch(`${BASE_URL}${endpoint}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          username: username.trim(),
          password
        })
      });

      if (!response.ok) {
        const text = await response.text();
        let message = 'Something went wrong';

        try {
          const json = JSON.parse(text);
          message = json.message || json.error || message;
        } catch {
          if (response.status === 401) {
            message = 'Invalid credentials';
          } else if (response.status === 409) {
            message = 'Username already exists';
          } else if (response.status === 400) {
            message = 'Please check your username and password';
          } else if (response.status === 404 && isSignUp) {
            message = 'Registration is not available yet';
          } else if (response.status >= 500) {
            message = 'Server error. Please try again later';
          }
        }

        throw new Error(message);
      }

      const data = await response.json();

      if (!data.token) {
        throw new Error('Authentication token was not returned');
      }

      localStorage.setItem('token', data.token);
      onLogin(data.token);
    } catch (err) {
      setError(err.message || 'Unable to connect to server');
      triggerShake();
    } finally {
      setLoading(false);
    }
  };

  const toggleMode = () => {
    setIsSignUp(!isSignUp);
    setError('');
    setPassword('');
    setConfirmPassword('');
  };

  return (
    <div className="login-page">
      <div className="login-bg">
        <div className="login-bg-orb login-bg-orb--1" />
        <div className="login-bg-orb login-bg-orb--2" />
        <div className="login-bg-orb login-bg-orb--3" />
      </div>

      <div className={`login-card ${shake ? 'login-card--shake' : ''}`}>
        <div className="login-brand">
          <div className="login-logo">
            <svg
              width="40"
              height="40"
              viewBox="0 0 40 40"
              fill="none"
            >
              <rect
                width="40"
                height="40"
                rx="10"
                fill="url(#logo-gradient)"
              />
              <path
                d="M12 14h16v3H12zM12 20h12v3H12zM12 26h8v3H12z"
                fill="white"
                opacity="0.9"
              />
              <defs>
                <linearGradient
                  id="logo-gradient"
                  x1="0"
                  y1="0"
                  x2="40"
                  y2="40"
                >
                  <stop stopColor="#6366f1" />
                  <stop offset="1" stopColor="#a855f7" />
                </linearGradient>
              </defs>
            </svg>
          </div>

          <h1 className="login-title">JobFlow</h1>

          <p className="login-subtitle">
            {isSignUp
              ? 'Create your account'
              : 'Welcome back'}
          </p>
        </div>

        {error && (
          <div className="login-error" role="alert">
            <svg
              width="16"
              height="16"
              viewBox="0 0 16 16"
              fill="currentColor"
            >
              <path d="M8 1a7 7 0 100 14A7 7 0 008 1zm-.75 3.75a.75.75 0 011.5 0v3.5a.75.75 0 01-1.5 0v-3.5zM8 11a1 1 0 100 2 1 1 0 000-2z" />
            </svg>
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="login-form">
          <div className="login-field">
            <input
              ref={usernameRef}
              id="login-username"
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className="login-input"
              placeholder=" "
              autoComplete="username"
              disabled={loading}
              required
              minLength={3}
              maxLength={50}
            />
            <label
              htmlFor="login-username"
              className="login-label"
            >
              Username
            </label>
          </div>

          <div className="login-field">
            <input
              id="login-password"
              type={showPassword ? 'text' : 'password'}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="login-input"
              placeholder=" "
              autoComplete={
                isSignUp ? 'new-password' : 'current-password'
              }
              disabled={loading}
              required
              minLength={isSignUp ? 8 : undefined}
              maxLength={72}
            />
            <label
              htmlFor="login-password"
              className="login-label"
            >
              Password
            </label>

            <button
              type="button"
              className="login-eye"
              onClick={() => setShowPassword(!showPassword)}
              tabIndex={-1}
              aria-label={
                showPassword ? 'Hide password' : 'Show password'
              }
            >
              {showPassword
                ? <EyeOff size={18} />
                : <Eye size={18} />}
            </button>
          </div>

          {isSignUp && (
            <div className="login-field login-field--enter">
              <input
                id="login-confirm"
                type={showConfirm ? 'text' : 'password'}
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                className="login-input"
                placeholder=" "
                autoComplete="new-password"
                disabled={loading}
                required
                maxLength={72}
              />

              <label
                htmlFor="login-confirm"
                className="login-label"
              >
                Confirm Password
              </label>

              <button
                type="button"
                className="login-eye"
                onClick={() => setShowConfirm(!showConfirm)}
                tabIndex={-1}
                aria-label={
                  showConfirm ? 'Hide password' : 'Show password'
                }
              >
                {showConfirm
                  ? <EyeOff size={18} />
                  : <Eye size={18} />}
              </button>
            </div>
          )}

          <button
            type="submit"
            className="login-submit"
            disabled={loading}
          >
            {loading ? (
              <Loader2 size={20} className="login-spinner" />
            ) : (
              <>
                {isSignUp
                  ? <UserPlus size={18} />
                  : <LogIn size={18} />}

                {isSignUp ? 'Create Account' : 'Sign In'}

                <ArrowRight
                  size={16}
                  className="login-arrow"
                />
              </>
            )}
          </button>
        </form>

        <div className="login-toggle">
          <span>
            {isSignUp
              ? 'Already have an account?'
              : "Don't have an account?"}
          </span>

          <button
            type="button"
            onClick={toggleMode}
            className="login-toggle-btn"
            disabled={loading}
          >
            {isSignUp ? 'Sign In' : 'Sign Up'}
          </button>
        </div>
      </div>
    </div>
  );
}

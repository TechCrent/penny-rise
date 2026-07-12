import React, { createContext, useContext, useState, useEffect, useRef } from 'react';
import { clearSession, loadSession, persistSession, setSessionListener } from './authSession';
import { isAccessTokenExpired, decodeKycStatusFromJwt } from './jwt';

interface AuthState {
  accessToken: string | null;
  kycStatus: string | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  /**
   * True only when this session's authentication came from an in-app
   * setTokens() call (a fresh login), not from restoring a stored session
   * on cold start. Used to gate the one-time app-lock setup prompt so it
   * doesn't reappear on every app relaunch.
   */
  justLoggedIn: boolean;
}

interface AuthContextValue extends AuthState {
  setTokens: (accessToken: string, refreshToken: string, kycStatus: string) => Promise<void>;
  clearTokens: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function resolveKycStatus(accessToken: string, fallback: string): string {
  try {
    return decodeKycStatusFromJwt(accessToken);
  } catch {
    return fallback;
  }
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<AuthState>({
    accessToken: null,
    kycStatus: null,
    isLoading: true,
    isAuthenticated: false,
    justLoggedIn: false,
  });
  const justLoggedInRef = useRef(false);

  useEffect(() => {
    loadSession().then(async session => {
      if (session && isAccessTokenExpired(session.accessToken)) {
        await clearSession();
        setState({
          accessToken: null,
          kycStatus: null,
          isLoading: false,
          isAuthenticated: false,
          justLoggedIn: false,
        });
        return;
      }

      if (session) {
        setState({
          accessToken: session.accessToken,
          kycStatus: resolveKycStatus(session.accessToken, session.kycStatus),
          isLoading: false,
          isAuthenticated: true,
          justLoggedIn: false,
        });
      } else {
        setState({
          accessToken: null,
          kycStatus: null,
          isLoading: false,
          isAuthenticated: false,
          justLoggedIn: false,
        });
      }
    });
  }, []);

  useEffect(() => {
    setSessionListener(session => {
      if (session) {
        const justLoggedIn = justLoggedInRef.current;
        justLoggedInRef.current = false;
        setState({
          accessToken: session.accessToken,
          kycStatus: resolveKycStatus(session.accessToken, session.kycStatus),
          isLoading: false,
          isAuthenticated: true,
          justLoggedIn,
        });
      } else {
        setState({
          accessToken: null,
          kycStatus: null,
          isLoading: false,
          isAuthenticated: false,
          justLoggedIn: false,
        });
      }
    });

    return () => setSessionListener(null);
  }, []);

  const setTokens = async (accessToken: string, refreshToken: string, kycStatus: string) => {
    justLoggedInRef.current = true;
    await persistSession({ accessToken, refreshToken, kycStatus });
  };

  const clearTokens = async () => {
    await clearSession();
  };

  return (
    <AuthContext.Provider value={{ ...state, setTokens, clearTokens }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}

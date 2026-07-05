import React, { createContext, useContext, useState, useEffect } from 'react';
import { clearSession, loadSession, persistSession, setSessionListener } from './authSession';
import { isAccessTokenExpired, decodeKycStatusFromJwt } from './jwt';

interface AuthState {
  accessToken: string | null;
  kycStatus: string | null;
  isLoading: boolean;
  isAuthenticated: boolean;
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
  });

  useEffect(() => {
    loadSession().then(async session => {
      if (session && isAccessTokenExpired(session.accessToken)) {
        await clearSession();
        setState({
          accessToken: null,
          kycStatus: null,
          isLoading: false,
          isAuthenticated: false,
        });
        return;
      }

      if (session) {
        setState({
          accessToken: session.accessToken,
          kycStatus: resolveKycStatus(session.accessToken, session.kycStatus),
          isLoading: false,
          isAuthenticated: true,
        });
      } else {
        setState({
          accessToken: null,
          kycStatus: null,
          isLoading: false,
          isAuthenticated: false,
        });
      }
    });
  }, []);

  useEffect(() => {
    setSessionListener(session => {
      if (session) {
        setState({
          accessToken: session.accessToken,
          kycStatus: resolveKycStatus(session.accessToken, session.kycStatus),
          isLoading: false,
          isAuthenticated: true,
        });
      } else {
        setState({
          accessToken: null,
          kycStatus: null,
          isLoading: false,
          isAuthenticated: false,
        });
      }
    });

    return () => setSessionListener(null);
  }, []);

  const setTokens = async (accessToken: string, refreshToken: string, kycStatus: string) => {
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

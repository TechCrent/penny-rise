import React, { createContext, useContext, useState, useEffect } from 'react';
import * as SecureStore from 'expo-secure-store';

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

const ACCESS_TOKEN_KEY = 'stash_access_token';
const REFRESH_TOKEN_KEY = 'stash_refresh_token';
const KYC_STATUS_KEY = 'stash_kyc_status';

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<AuthState>({
    accessToken: null,
    kycStatus: null,
    isLoading: true,
    isAuthenticated: false,
  });

  useEffect(() => {
    Promise.all([
      SecureStore.getItemAsync(ACCESS_TOKEN_KEY),
      SecureStore.getItemAsync(KYC_STATUS_KEY),
    ]).then(([token, kyc]) => {
      setState({
        accessToken: token,
        kycStatus: kyc,
        isLoading: false,
        isAuthenticated: !!token,
      });
    });
  }, []);

  const setTokens = async (accessToken: string, refreshToken: string, kycStatus: string) => {
    await SecureStore.setItemAsync(ACCESS_TOKEN_KEY, accessToken);
    await SecureStore.setItemAsync(REFRESH_TOKEN_KEY, refreshToken);
    await SecureStore.setItemAsync(KYC_STATUS_KEY, kycStatus);
    setState({ accessToken, kycStatus, isLoading: false, isAuthenticated: true });
  };

  const clearTokens = async () => {
    await SecureStore.deleteItemAsync(ACCESS_TOKEN_KEY);
    await SecureStore.deleteItemAsync(REFRESH_TOKEN_KEY);
    await SecureStore.deleteItemAsync(KYC_STATUS_KEY);
    setState({
      accessToken: null,
      kycStatus: null,
      isLoading: false,
      isAuthenticated: false,
    });
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

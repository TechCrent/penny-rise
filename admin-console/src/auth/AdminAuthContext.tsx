import { createContext, useContext, useState, type ReactNode } from 'react';

interface AdminAuthState {
  isAuthenticated: boolean;
  isLoading: boolean;
  accountType: string | null;
}

interface AdminAuthContextValue extends AdminAuthState {
  login: (token: string) => void;
  logout: () => void;
}

const AdminAuthContext = createContext<AdminAuthContextValue | null>(null);

const SESSION_KEY = 'pennyrise_admin_token';

// Presentation-only decoding (no signature verification — that's the
// backend's job via @PreAuthorize) so nav links can be hidden for account
// types that don't have access, matching the account_type claim
// AdminJwtService puts in every admin JWT.
function decodeAccountType(token: string): string | null {
  try {
    const payload = token.split('.')[1];
    const decoded = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    const claims = JSON.parse(decoded);
    return typeof claims.account_type === 'string' ? claims.account_type : null;
  } catch {
    return null;
  }
}

function readInitialAuth(): AdminAuthState {
  try {
    const token = sessionStorage.getItem(SESSION_KEY);
    return {
      isAuthenticated: !!token,
      isLoading: false,
      accountType: token ? decodeAccountType(token) : null,
    };
  } catch {
    return { isAuthenticated: false, isLoading: false, accountType: null };
  }
}

export function AdminAuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AdminAuthState>(readInitialAuth);

  const login = (token: string) => {
    sessionStorage.setItem(SESSION_KEY, token);
    setState({ isAuthenticated: true, isLoading: false, accountType: decodeAccountType(token) });
  };

  const logout = () => {
    sessionStorage.removeItem(SESSION_KEY);
    setState({ isAuthenticated: false, isLoading: false, accountType: null });
  };

  return (
    <AdminAuthContext.Provider value={{ ...state, login, logout }}>
      {children}
    </AdminAuthContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components -- hook paired with provider
export function useAdminAuth() {
  const ctx = useContext(AdminAuthContext);
  if (!ctx) throw new Error('useAdminAuth must be used within AdminAuthProvider');
  return ctx;
}

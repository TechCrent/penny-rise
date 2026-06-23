import { createContext, useContext, useState, type ReactNode } from 'react';

interface AdminAuthState {
  isAuthenticated: boolean;
  isLoading: boolean;
}

interface AdminAuthContextValue extends AdminAuthState {
  login: (token: string) => void;
  logout: () => void;
}

const AdminAuthContext = createContext<AdminAuthContextValue | null>(null);

const SESSION_KEY = 'stash_admin_token';

function readInitialAuth(): AdminAuthState {
  try {
    const token = sessionStorage.getItem(SESSION_KEY);
    return { isAuthenticated: !!token, isLoading: false };
  } catch {
    return { isAuthenticated: false, isLoading: false };
  }
}

export function AdminAuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AdminAuthState>(readInitialAuth);

  const login = (token: string) => {
    sessionStorage.setItem(SESSION_KEY, token);
    setState({ isAuthenticated: true, isLoading: false });
  };

  const logout = () => {
    sessionStorage.removeItem(SESSION_KEY);
    setState({ isAuthenticated: false, isLoading: false });
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

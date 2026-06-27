import { useAuth as _useAuth } from '../auth/AuthContext';

interface UserProfile {
  momoNumber?: string | null;
}

export function useAuth() {
  const auth = _useAuth();
  return { ...auth, user: null as UserProfile | null };
}

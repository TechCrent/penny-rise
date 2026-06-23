import * as SecureStore from 'expo-secure-store';

const ACCESS_TOKEN_KEY = 'stash_access_token';
const REFRESH_TOKEN_KEY = 'stash_refresh_token';
const KYC_STATUS_KEY = 'stash_kyc_status';

export interface SessionTokens {
  accessToken: string;
  refreshToken: string;
  kycStatus: string;
}

type SessionListener = (session: SessionTokens | null) => void;

let sessionListener: SessionListener | null = null;

export function setSessionListener(listener: SessionListener | null): void {
  sessionListener = listener;
}

export async function getAccessToken(): Promise<string | null> {
  return SecureStore.getItemAsync(ACCESS_TOKEN_KEY);
}

export async function getRefreshToken(): Promise<string | null> {
  return SecureStore.getItemAsync(REFRESH_TOKEN_KEY);
}

export async function getKycStatus(): Promise<string | null> {
  return SecureStore.getItemAsync(KYC_STATUS_KEY);
}

export async function loadSession(): Promise<SessionTokens | null> {
  const [accessToken, refreshToken, kycStatus] = await Promise.all([
    getAccessToken(),
    getRefreshToken(),
    getKycStatus(),
  ]);

  if (!accessToken || !refreshToken || !kycStatus) {
    return null;
  }

  return { accessToken, refreshToken, kycStatus };
}

export async function persistSession(session: SessionTokens): Promise<void> {
  await SecureStore.setItemAsync(ACCESS_TOKEN_KEY, session.accessToken);
  await SecureStore.setItemAsync(REFRESH_TOKEN_KEY, session.refreshToken);
  await SecureStore.setItemAsync(KYC_STATUS_KEY, session.kycStatus);
  sessionListener?.(session);
}

export async function clearSession(): Promise<void> {
  await SecureStore.deleteItemAsync(ACCESS_TOKEN_KEY);
  await SecureStore.deleteItemAsync(REFRESH_TOKEN_KEY);
  await SecureStore.deleteItemAsync(KYC_STATUS_KEY);
  sessionListener?.(null);
}

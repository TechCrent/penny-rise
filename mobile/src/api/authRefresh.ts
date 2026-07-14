import axios from 'axios';

const BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? 'http://localhost:8080';

export interface RefreshResponse {
  access_token: string;
  refresh_token: string;
  expires_in: number;
}

/** Uses a plain axios instance so refresh does not recurse through apiClient interceptors. */
export async function performTokenRefresh(refreshToken: string): Promise<RefreshResponse> {
  const { data } = await axios.post<RefreshResponse>(
    `${BASE_URL}/api/v1/auth/refresh`,
    {
      refresh_token: refreshToken,
      device_id: 'mobile',
      device_label: 'PennyRise Mobile',
    },
    {
      timeout: 15_000,
      headers: { 'Content-Type': 'application/json' },
    },
  );
  return data;
}

import * as SecureStore from 'expo-secure-store';

const APP_LOCK_ENABLED_KEY = 'stash_app_lock_enabled';

export async function isAppLockEnabled(): Promise<boolean> {
  const value = await SecureStore.getItemAsync(APP_LOCK_ENABLED_KEY);
  return value === 'true';
}

export async function setAppLockEnabled(enabled: boolean): Promise<void> {
  await SecureStore.setItemAsync(APP_LOCK_ENABLED_KEY, enabled ? 'true' : 'false');
}

import * as SecureStore from 'expo-secure-store';
import * as Crypto from 'expo-crypto';

const APP_LOCK_ENABLED_KEY = 'stash_app_lock_enabled';
const APP_LOCK_METHOD_KEY = 'stash_app_lock_method';
const APP_LOCK_PIN_HASH_KEY = 'stash_app_lock_pin_hash';
const APP_LOCK_PIN_SALT_KEY = 'stash_app_lock_pin_salt';
const APP_LOCK_PROMPTED_KEY = 'stash_app_lock_prompted';

export type AppLockMethod = 'system' | 'pin' | 'both';

export async function isAppLockEnabled(): Promise<boolean> {
  const value = await SecureStore.getItemAsync(APP_LOCK_ENABLED_KEY);
  return value === 'true';
}

export async function setAppLockEnabled(enabled: boolean): Promise<void> {
  await SecureStore.setItemAsync(APP_LOCK_ENABLED_KEY, enabled ? 'true' : 'false');
}

export async function getAppLockMethod(): Promise<AppLockMethod | null> {
  const value = await SecureStore.getItemAsync(APP_LOCK_METHOD_KEY);
  return value === 'system' || value === 'pin' || value === 'both' ? value : null;
}

export async function setAppLockMethod(method: AppLockMethod): Promise<void> {
  await SecureStore.setItemAsync(APP_LOCK_METHOD_KEY, method);
}

export async function hasPin(): Promise<boolean> {
  const hash = await SecureStore.getItemAsync(APP_LOCK_PIN_HASH_KEY);
  return !!hash;
}

/** Hashes the PIN with a random per-install salt — the raw PIN is never persisted. */
export async function setPin(pin: string): Promise<void> {
  const saltBytes = await Crypto.getRandomBytesAsync(16);
  const salt = Array.from(saltBytes)
    .map(b => b.toString(16).padStart(2, '0'))
    .join('');
  const hash = await Crypto.digestStringAsync(Crypto.CryptoDigestAlgorithm.SHA256, salt + pin);
  await SecureStore.setItemAsync(APP_LOCK_PIN_SALT_KEY, salt);
  await SecureStore.setItemAsync(APP_LOCK_PIN_HASH_KEY, hash);
}

export async function verifyPin(pin: string): Promise<boolean> {
  const [salt, storedHash] = await Promise.all([
    SecureStore.getItemAsync(APP_LOCK_PIN_SALT_KEY),
    SecureStore.getItemAsync(APP_LOCK_PIN_HASH_KEY),
  ]);
  if (!salt || !storedHash) return false;
  const hash = await Crypto.digestStringAsync(Crypto.CryptoDigestAlgorithm.SHA256, salt + pin);
  return hash === storedHash;
}

export async function clearPin(): Promise<void> {
  await SecureStore.deleteItemAsync(APP_LOCK_PIN_SALT_KEY);
  await SecureStore.deleteItemAsync(APP_LOCK_PIN_HASH_KEY);
}

export async function hasPromptedAppLockSetup(): Promise<boolean> {
  const value = await SecureStore.getItemAsync(APP_LOCK_PROMPTED_KEY);
  return value === 'true';
}

export async function markAppLockSetupPrompted(): Promise<void> {
  await SecureStore.setItemAsync(APP_LOCK_PROMPTED_KEY, 'true');
}

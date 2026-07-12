const mockStore = new Map<string, string>();

jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn((key: string) => Promise.resolve(mockStore.get(key) ?? null)),
  setItemAsync: jest.fn((key: string, value: string) => {
    mockStore.set(key, value);
    return Promise.resolve();
  }),
  deleteItemAsync: jest.fn((key: string) => {
    mockStore.delete(key);
    return Promise.resolve();
  }),
}));

// jest-expo's built-in expo-crypto mock returns constant zeroed/empty output
// regardless of input, which would make verifyPin() trivially pass for any
// PIN. Swap in a real Node crypto-backed implementation so the hash actually
// varies with the input, matching the runtime module's web fallback.
jest.mock('expo-crypto', () => {
  const nodeCrypto = require('crypto');
  return {
    CryptoDigestAlgorithm: { SHA256: 'SHA-256' },
    getRandomBytesAsync: (byteCount: number) =>
      Promise.resolve(new Uint8Array(nodeCrypto.randomBytes(byteCount))),
    digestStringAsync: (_algorithm: string, data: string) =>
      Promise.resolve(nodeCrypto.createHash('sha256').update(data).digest('hex')),
  };
});

import {
  isAppLockEnabled,
  setAppLockEnabled,
  getAppLockMethod,
  setAppLockMethod,
  hasPin,
  setPin,
  verifyPin,
  clearPin,
  hasPromptedAppLockSetup,
  markAppLockSetupPrompted,
} from '../src/auth/appLock';

describe('appLock storage', () => {
  beforeEach(() => {
    mockStore.clear();
  });

  it('defaults to disabled, no method, no PIN, and not prompted', async () => {
    expect(await isAppLockEnabled()).toBe(false);
    expect(await getAppLockMethod()).toBeNull();
    expect(await hasPin()).toBe(false);
    expect(await hasPromptedAppLockSetup()).toBe(false);
  });

  it('persists the enabled flag and method', async () => {
    await setAppLockEnabled(true);
    await setAppLockMethod('both');
    expect(await isAppLockEnabled()).toBe(true);
    expect(await getAppLockMethod()).toBe('both');
  });

  it('marks setup as prompted', async () => {
    await markAppLockSetupPrompted();
    expect(await hasPromptedAppLockSetup()).toBe(true);
  });

  it('verifies a correct PIN and rejects an incorrect one', async () => {
    await setPin('123456');
    expect(await hasPin()).toBe(true);
    expect(await verifyPin('123456')).toBe(true);
    expect(await verifyPin('000000')).toBe(false);
  });

  it('never stores the raw PIN value', async () => {
    await setPin('123456');
    const rawValues = Array.from(mockStore.values());
    expect(rawValues).not.toContain('123456');
  });

  it('clears the PIN so verification and hasPin both fail afterwards', async () => {
    await setPin('123456');
    await clearPin();
    expect(await hasPin()).toBe(false);
    expect(await verifyPin('123456')).toBe(false);
  });

  it('verifyPin returns false when no PIN has been set', async () => {
    expect(await verifyPin('123456')).toBe(false);
  });
});

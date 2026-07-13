import React, { useCallback, useEffect, useRef, useState } from 'react';
import { AppState, type AppStateStatus, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import * as LocalAuthentication from 'expo-local-authentication';
import { PrimaryButton } from '../components/PrimaryButton';
import { PinEntryPad } from '../components/PinEntryPad';
import { isAppLockEnabled, getAppLockMethod, verifyPin, type AppLockMethod } from './appLock';
import { useAuth } from './AuthContext';
import { colors, radii, spacing, typography } from '../theme';
import { Icon } from '../components/ui';

type UnlockMode = 'system' | 'pin';

/**
 * Wraps the authenticated app tree. When App Lock is enabled
 * (Settings > App Lock), requires unlocking via system biometrics/passcode,
 * a 6-digit in-app PIN, or both — on cold start and whenever the app
 * returns to the foreground from the background.
 */
export function AppLockGate({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuth();
  const [lockEnabled, setLockEnabled] = useState(false);
  const [method, setMethod] = useState<AppLockMethod | null>(null);
  const [locked, setLocked] = useState(false);
  const [checking, setChecking] = useState(true);
  const [unlockMode, setUnlockMode] = useState<UnlockMode>('system');
  const [pinError, setPinError] = useState<string | undefined>(undefined);
  const [pinResetSignal, setPinResetSignal] = useState(0);
  const appState = useRef(AppState.currentState);

  const promptSystemUnlock = useCallback(async () => {
    const result = await LocalAuthentication.authenticateAsync({
      promptMessage: 'Unlock Stash',
      disableDeviceFallback: false,
    });
    if (result.success) {
      setLocked(false);
    }
  }, []);

  useEffect(() => {
    if (!isAuthenticated) {
      setLockEnabled(false);
      setLocked(false);
      setChecking(false);
      return;
    }
    Promise.all([isAppLockEnabled(), getAppLockMethod()]).then(([enabled, storedMethod]) => {
      setLockEnabled(enabled);
      setMethod(storedMethod);
      setLocked(enabled);
      setUnlockMode(storedMethod === 'pin' ? 'pin' : 'system');
      setChecking(false);
    });
  }, [isAuthenticated]);

  useEffect(() => {
    if (!checking && lockEnabled && locked && unlockMode === 'system') {
      promptSystemUnlock();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [checking, lockEnabled, locked, unlockMode]);

  useEffect(() => {
    const subscription = AppState.addEventListener('change', (nextState: AppStateStatus) => {
      if (
        appState.current.match(/active/) &&
        (nextState === 'background' || nextState === 'inactive') &&
        lockEnabled
      ) {
        setLocked(true);
        setUnlockMode(method === 'pin' ? 'pin' : 'system');
        setPinError(undefined);
      }
      appState.current = nextState;
    });

    return () => subscription.remove();
  }, [lockEnabled, method]);

  const onPinComplete = async (pin: string) => {
    const valid = await verifyPin(pin);
    if (valid) {
      setLocked(false);
      setPinError(undefined);
      return;
    }
    setPinError('Incorrect PIN. Try again.');
    setPinResetSignal(s => s + 1);
  };

  if (checking) {
    return null;
  }

  if (isAuthenticated && lockEnabled && locked) {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.content}>
          <View style={styles.iconBadge}>
            <Icon name="lock-closed" size={28} color={colors.gold.text} />
          </View>
          <Text style={styles.heading}>Stash is locked</Text>

          {unlockMode === 'pin' ? (
            <>
              <Text style={styles.body}>Enter your 6-digit PIN to continue.</Text>
              <PinEntryPad
                onComplete={onPinComplete}
                error={pinError}
                resetSignal={pinResetSignal}
              />
            </>
          ) : (
            <>
              <Text style={styles.body}>
                Unlock with your device biometrics or passcode to continue.
              </Text>
              <PrimaryButton
                title="Unlock"
                onPress={promptSystemUnlock}
                style={styles.button}
              />
            </>
          )}

          {method === 'both' ? (
            <TouchableOpacity
              onPress={() => {
                setPinError(undefined);
                setUnlockMode(unlockMode === 'pin' ? 'system' : 'pin');
              }}
              style={styles.switchButton}
            >
              <Text style={styles.switchText}>
                {unlockMode === 'pin' ? 'Use device unlock instead' : 'Use PIN instead'}
              </Text>
            </TouchableOpacity>
          ) : null}
        </View>
      </SafeAreaView>
    );
  }

  return <>{children}</>;
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  content: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing['3xl'] },
  iconBadge: {
    width: 72,
    height: 72,
    borderRadius: radii.pill,
    backgroundColor: colors.gold.light,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing['2xl'],
  },
  heading: { ...typography.h2, color: colors.textPrimary, marginBottom: spacing.sm },
  body: { fontSize: 15, color: colors.textSecondary, textAlign: 'center', marginBottom: spacing['2xl'] },
  button: { alignSelf: 'stretch' },
  switchButton: { marginTop: spacing['2xl'], padding: spacing.sm },
  switchText: { color: colors.textPrimary, fontSize: 14, fontWeight: '500' },
});

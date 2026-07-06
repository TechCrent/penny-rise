import React, { useCallback, useEffect, useRef, useState } from 'react';
import { AppState, type AppStateStatus, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import * as LocalAuthentication from 'expo-local-authentication';
import { PrimaryButton } from '../components/PrimaryButton';
import { isAppLockEnabled } from './appLock';
import { useAuth } from './AuthContext';

/**
 * Wraps the authenticated app tree. When App Lock is enabled
 * (Settings > App Lock), requires a biometric/device-passcode prompt on
 * cold start and whenever the app returns to the foreground from the
 * background — a standard expectation for an app holding real cash
 * balances, which had no lock of any kind before this.
 */
export function AppLockGate({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuth();
  const [lockEnabled, setLockEnabled] = useState(false);
  const [locked, setLocked] = useState(false);
  const [checking, setChecking] = useState(true);
  const appState = useRef(AppState.currentState);

  const promptUnlock = useCallback(async () => {
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
    isAppLockEnabled().then(enabled => {
      setLockEnabled(enabled);
      setLocked(enabled);
      setChecking(false);
    });
  }, [isAuthenticated]);

  useEffect(() => {
    if (!checking && lockEnabled && locked) {
      promptUnlock();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [checking, lockEnabled, locked]);

  useEffect(() => {
    const subscription = AppState.addEventListener('change', (nextState: AppStateStatus) => {
      if (
        appState.current.match(/active/) &&
        (nextState === 'background' || nextState === 'inactive') &&
        lockEnabled
      ) {
        setLocked(true);
      }
      appState.current = nextState;
    });

    return () => subscription.remove();
  }, [lockEnabled]);

  if (checking) {
    return null;
  }

  if (isAuthenticated && lockEnabled && locked) {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.content}>
          <Text style={styles.emoji}>🔒</Text>
          <Text style={styles.heading}>Stash is locked</Text>
          <Text style={styles.body}>
            Unlock with your device biometrics or passcode to continue.
          </Text>
          <PrimaryButton title="Unlock" onPress={promptUnlock} style={styles.button} />
        </View>
      </SafeAreaView>
    );
  }

  return <>{children}</>;
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#FFFFFF' },
  content: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 32 },
  emoji: { fontSize: 56, marginBottom: 24 },
  heading: { fontSize: 22, fontWeight: '700', color: '#111827', marginBottom: 8 },
  body: { fontSize: 15, color: '#6B7280', textAlign: 'center', marginBottom: 32 },
  button: { alignSelf: 'stretch' },
});

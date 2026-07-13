import React, { useEffect, useState } from 'react';
import { View, Text, TouchableOpacity, StyleSheet, Switch, ActivityIndicator } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import * as LocalAuthentication from 'expo-local-authentication';
import type { RootStackParamList } from '../../navigation/RootNavigator';
import { PinEntryPad } from '../../components/PinEntryPad';
import { colors, spacing, typography } from '../../theme';
import {
  isAppLockEnabled,
  setAppLockEnabled,
  getAppLockMethod,
  setAppLockMethod,
  setPin,
  clearPin,
  hasPin,
  type AppLockMethod,
} from '../../auth/appLock';

type Nav = NativeStackNavigationProp<RootStackParamList, 'AppLockSettings'>;

type Step = 'main' | 'choose-method' | 'create-pin' | 'confirm-pin';

export function AppLockSettingsScreen() {
  const navigation = useNavigation<Nav>();
  const [loading, setLoading] = useState(true);
  const [enabled, setEnabled] = useState(false);
  const [method, setMethod] = useState<AppLockMethod | null>(null);
  const [pinSet, setPinSet] = useState(false);
  const [hardwareAvailable, setHardwareAvailable] = useState(true);
  const [step, setStep] = useState<Step>('main');
  const [error, setError] = useState<string | undefined>(undefined);
  const [firstPin, setFirstPin] = useState('');
  const [pinError, setPinError] = useState<string | undefined>(undefined);
  const [resetSignal, setResetSignal] = useState(0);
  const [pendingMethod, setPendingMethod] = useState<AppLockMethod | null>(null);

  const load = async () => {
    const [currentlyEnabled, currentMethod, currentPinSet, hasHardware, isEnrolled] =
      await Promise.all([
        isAppLockEnabled(),
        getAppLockMethod(),
        hasPin(),
        LocalAuthentication.hasHardwareAsync(),
        LocalAuthentication.isEnrolledAsync(),
      ]);
    setEnabled(currentlyEnabled);
    setMethod(currentMethod);
    setPinSet(currentPinSet);
    setHardwareAvailable(hasHardware && isEnrolled);
    setLoading(false);
  };

  useEffect(() => {
    load();
  }, []);

  const onToggle = async (value: boolean) => {
    setError(undefined);

    if (!value) {
      await setAppLockEnabled(false);
      setEnabled(false);
      return;
    }

    if (method) {
      // Re-enabling with a previously configured method — no need to verify
      // hardware again for PIN-only; system/both still need it.
      if (method !== 'pin') {
        const result = await LocalAuthentication.authenticateAsync({
          promptMessage: 'Confirm it’s you to enable App Lock',
        });
        if (!result.success) {
          setError('Could not verify your identity. App Lock was not enabled.');
          return;
        }
      }
      await setAppLockEnabled(true);
      setEnabled(true);
      return;
    }

    setStep('choose-method');
  };

  const chooseSystem = async () => {
    const result = await LocalAuthentication.authenticateAsync({
      promptMessage: 'Confirm it’s you to enable App Lock',
    });
    if (!result.success) {
      setError('Could not verify your identity. App Lock was not enabled.');
      setStep('main');
      return;
    }
    await setAppLockMethod('system');
    await setAppLockEnabled(true);
    await load();
    setStep('main');
  };

  const choosePin = () => {
    setPendingMethod('pin');
    setPinError(undefined);
    setStep('create-pin');
  };

  const chooseBoth = async () => {
    const result = await LocalAuthentication.authenticateAsync({
      promptMessage: 'Confirm it’s you to enable App Lock',
    });
    if (!result.success) {
      setError('Could not verify your identity. App Lock was not enabled.');
      setStep('main');
      return;
    }
    setPendingMethod('both');
    setPinError(undefined);
    setStep('create-pin');
  };

  const startChangePin = () => {
    setPendingMethod(method === 'both' ? 'both' : 'pin');
    setPinError(undefined);
    setStep('create-pin');
  };

  const onFirstPinComplete = (pin: string) => {
    setFirstPin(pin);
    setPinError(undefined);
    setResetSignal(s => s + 1);
    setStep('confirm-pin');
  };

  const onConfirmPinComplete = async (pin: string) => {
    if (pin !== firstPin) {
      setPinError('PINs did not match. Try again.');
      setResetSignal(s => s + 1);
      setStep('create-pin');
      setFirstPin('');
      return;
    }
    await setPin(pin);
    await setAppLockMethod(pendingMethod ?? 'pin');
    await setAppLockEnabled(true);
    await load();
    setStep('main');
    setFirstPin('');
  };

  const removePin = async () => {
    await clearPin();
    await setAppLockMethod('system');
    await load();
  };

  if (step === 'choose-method') {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.header}>
          <TouchableOpacity onPress={() => setStep('main')} hitSlop={8}>
            <Text style={styles.backText}>← Back</Text>
          </TouchableOpacity>
        </View>
        <Text style={styles.heading}>Choose unlock method</Text>
        {error ? <Text style={styles.errorText}>{error}</Text> : null}
        {hardwareAvailable ? (
          <TouchableOpacity style={styles.optionRow} onPress={chooseSystem}>
            <Text style={styles.rowLabel}>Device unlock (Face ID / fingerprint / passcode)</Text>
          </TouchableOpacity>
        ) : null}
        <TouchableOpacity style={styles.optionRow} onPress={choosePin}>
          <Text style={styles.rowLabel}>6-digit PIN</Text>
        </TouchableOpacity>
        {hardwareAvailable ? (
          <TouchableOpacity style={styles.optionRow} onPress={chooseBoth}>
            <Text style={styles.rowLabel}>Both</Text>
          </TouchableOpacity>
        ) : null}
      </SafeAreaView>
    );
  }

  if (step === 'create-pin' || step === 'confirm-pin') {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.pinContent}>
          <Text style={styles.heading}>
            {step === 'create-pin' ? 'Create your 6-digit PIN' : 'Confirm your PIN'}
          </Text>
          <Text style={styles.subheading}>
            {step === 'create-pin'
              ? 'You will use this to unlock Stash.'
              : 'Enter the same PIN again.'}
          </Text>
          <PinEntryPad
            key={step}
            onComplete={step === 'create-pin' ? onFirstPinComplete : onConfirmPinComplete}
            error={pinError}
            resetSignal={resetSignal}
          />
          <TouchableOpacity onPress={() => setStep('main')} style={styles.cancelButton}>
            <Text style={styles.skipText}>Cancel</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} hitSlop={8}>
          <Text style={styles.backText}>← Back</Text>
        </TouchableOpacity>
      </View>

      <Text style={styles.heading}>App Lock</Text>
      <Text style={styles.subheading}>
        Require unlocking to open Stash after it&apos;s been backgrounded. Optional — use your
        device unlock, a 6-digit PIN, or both.
      </Text>

      {loading ? (
        <ActivityIndicator style={styles.loading} color={colors.gold.base} />
      ) : !hardwareAvailable && !pinSet ? (
        <Text style={styles.unavailableText}>
          No biometrics or device passcode is set up on this device. You can still use a 6-digit
          PIN below.
        </Text>
      ) : null}

      {!loading && (
        <>
          <View style={styles.row}>
            <Text style={styles.rowLabel}>Require unlock on launch</Text>
            <Switch
              value={enabled}
              onValueChange={onToggle}
              trackColor={{ true: colors.gold.base, false: colors.neutral[300] }}
              thumbColor={colors.neutral[0]}
            />
          </View>

          {enabled && method ? (
            <>
              <View style={styles.row}>
                <Text style={styles.rowLabel}>Method</Text>
                <Text style={styles.methodValue}>
                  {method === 'system' ? 'Device unlock' : method === 'pin' ? 'PIN' : 'Both'}
                </Text>
              </View>
              <TouchableOpacity onPress={() => setStep('choose-method')} style={styles.linkRow}>
                <Text style={styles.linkText}>Change method</Text>
              </TouchableOpacity>
              {method !== 'system' ? (
                <>
                  <TouchableOpacity onPress={startChangePin} style={styles.linkRow}>
                    <Text style={styles.linkText}>Change PIN</Text>
                  </TouchableOpacity>
                  <TouchableOpacity onPress={removePin} style={styles.linkRow}>
                    <Text style={styles.linkTextDanger}>Remove PIN</Text>
                  </TouchableOpacity>
                </>
              ) : null}
            </>
          ) : null}
        </>
      )}

      {error ? <Text style={styles.errorText}>{error}</Text> : null}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background, paddingHorizontal: spacing.xl },
  header: { marginBottom: spacing['2xl'], marginTop: spacing.sm },
  backText: { color: colors.textPrimary, fontSize: 15 },
  heading: { ...typography.h1, color: colors.textPrimary, marginBottom: spacing.sm },
  subheading: { fontSize: 14, color: colors.textSecondary, marginBottom: spacing['3xl'], lineHeight: 20 },
  loading: { marginTop: spacing.xl },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: spacing.md,
    borderTopWidth: 1,
    borderBottomWidth: 1,
    borderColor: colors.border,
  },
  rowLabel: { ...typography.bodyMedium, color: colors.textPrimary },
  methodValue: { fontSize: 15, color: colors.textSecondary },
  linkRow: { paddingVertical: spacing.md },
  linkText: { fontSize: 15, color: colors.textPrimary, fontWeight: '500' },
  linkTextDanger: { fontSize: 15, color: colors.status.error, fontWeight: '500' },
  unavailableText: { fontSize: 13, color: colors.textTertiary, lineHeight: 19, marginBottom: spacing.md },
  errorText: { color: colors.status.error, fontSize: 13, marginTop: spacing.lg },
  optionRow: {
    paddingVertical: spacing.lg,
    borderBottomWidth: 1,
    borderColor: colors.border,
  },
  pinContent: { flex: 1, justifyContent: 'center' },
  cancelButton: { alignItems: 'center', marginTop: spacing.lg, padding: spacing.sm },
  skipText: { color: colors.textSecondary, fontSize: 14, fontWeight: '500' },
});

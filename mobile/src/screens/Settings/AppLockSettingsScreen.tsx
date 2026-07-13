import React, { useEffect, useState } from 'react';
import {
  View,
  Text,
  ScrollView,
  TouchableOpacity,
  StyleSheet,
  Switch,
  ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import Animated from 'react-native-reanimated';
import * as LocalAuthentication from 'expo-local-authentication';
import type { RootStackParamList } from '../../navigation/RootNavigator';
import { PinEntryPad } from '../../components/PinEntryPad';
import {
  Banner,
  Icon,
  PressableScale,
  ScreenHeader,
  fadeInUp,
  type IconName,
} from '../../components/ui';
import { colors, radii, shadows, spacing, typography } from '../../theme';
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
        <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
          <ScreenHeader title="Choose unlock method" onBack={() => setStep('main')} />
          {error ? <Banner tone="error" message={error} /> : null}
          <Animated.View entering={fadeInUp(60)} style={styles.card}>
            {hardwareAvailable ? (
              <OptionRow
                icon="shield-checkmark-outline"
                label="Device unlock (Face ID / fingerprint / passcode)"
                onPress={chooseSystem}
              />
            ) : null}
            <OptionRow
              icon="lock-closed-outline"
              label="6-digit PIN"
              onPress={choosePin}
              isLast={!hardwareAvailable}
            />
            {hardwareAvailable ? (
              <OptionRow icon="sparkles" label="Both" onPress={chooseBoth} isLast />
            ) : null}
          </Animated.View>
        </ScrollView>
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
      <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
        <ScreenHeader title="App Lock" onBack={() => navigation.goBack()} />
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
            <Animated.View entering={fadeInUp(60)} style={styles.card}>
              <View style={styles.row}>
                <View style={styles.rowLeft}>
                  <View style={styles.badge}>
                    <Icon name="lock-closed-outline" size={18} color={colors.gold.text} />
                  </View>
                  <Text style={styles.rowLabel}>Require unlock on launch</Text>
                </View>
                <Switch
                  value={enabled}
                  onValueChange={onToggle}
                  trackColor={{ true: colors.gold.base, false: colors.neutral[300] }}
                  thumbColor={colors.neutral[0]}
                />
              </View>
            </Animated.View>

            {enabled && method ? (
              <Animated.View entering={fadeInUp(120)} style={styles.card}>
                <View style={[styles.row, styles.rowDivider]}>
                  <Text style={styles.rowLabel}>Method</Text>
                  <Text style={styles.methodValue}>
                    {method === 'system' ? 'Device unlock' : method === 'pin' ? 'PIN' : 'Both'}
                  </Text>
                </View>
                <LinkRow
                  label="Change method"
                  icon="swap-horizontal-outline"
                  onPress={() => setStep('choose-method')}
                  isLast={method === 'system'}
                />
                {method !== 'system' ? (
                  <>
                    <LinkRow label="Change PIN" icon="lock-closed-outline" onPress={startChangePin} />
                    <LinkRow
                      label="Remove PIN"
                      icon="close-circle"
                      onPress={removePin}
                      destructive
                      isLast
                    />
                  </>
                ) : null}
              </Animated.View>
            ) : null}
          </>
        )}

        {error ? <Banner tone="error" message={error} /> : null}
      </ScrollView>
    </SafeAreaView>
  );
}

interface OptionRowProps {
  icon: IconName;
  label: string;
  onPress: () => void;
  isLast?: boolean;
}

function OptionRow({ icon, label, onPress, isLast }: OptionRowProps) {
  return (
    <PressableScale style={[styles.optionRow, isLast ? styles.rowLast : null]} onPress={onPress}>
      <View style={styles.badge}>
        <Icon name={icon} size={18} color={colors.gold.text} />
      </View>
      <Text style={styles.optionLabel}>{label}</Text>
      <Icon name="arrow-forward" size={18} color={colors.textTertiary} />
    </PressableScale>
  );
}

interface LinkRowProps {
  label: string;
  icon: IconName;
  onPress: () => void;
  destructive?: boolean;
  isLast?: boolean;
}

function LinkRow({ label, icon, onPress, destructive, isLast }: LinkRowProps) {
  return (
    <PressableScale
      style={[styles.row, styles.linkRow, isLast ? styles.rowLast : null]}
      onPress={onPress}
    >
      <View style={styles.rowLeft}>
        <View style={[styles.badge, destructive ? styles.badgeDestructive : null]}>
          <Icon name={icon} size={18} color={destructive ? colors.status.error : colors.gold.text} />
        </View>
        <Text style={[styles.linkText, destructive ? styles.linkTextDanger : null]}>{label}</Text>
      </View>
      <Icon name="arrow-forward" size={18} color={colors.textTertiary} />
    </PressableScale>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  scroll: { paddingHorizontal: spacing.xl, paddingTop: spacing.md, paddingBottom: spacing['4xl'] },
  heading: { ...typography.h1, color: colors.textPrimary, marginBottom: spacing.sm },
  subheading: {
    fontSize: 14,
    color: colors.textSecondary,
    marginBottom: spacing['2xl'],
    lineHeight: 20,
  },
  loading: { marginTop: spacing.xl },
  card: {
    backgroundColor: colors.surface,
    borderRadius: radii.lg,
    borderWidth: 1,
    borderColor: colors.border,
    overflow: 'hidden',
    marginBottom: spacing.lg,
    ...shadows.sm,
  },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    minHeight: 56,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
  },
  rowLeft: { flexDirection: 'row', alignItems: 'center', gap: spacing.md, flex: 1 },
  rowDivider: { borderBottomWidth: 1, borderBottomColor: colors.neutral[100] },
  rowLast: { borderBottomWidth: 0 },
  badge: {
    width: 38,
    height: 38,
    borderRadius: radii.md,
    backgroundColor: colors.gold.light,
    alignItems: 'center',
    justifyContent: 'center',
  },
  badgeDestructive: { backgroundColor: colors.status.errorBg },
  rowLabel: { ...typography.bodyMedium, color: colors.textPrimary },
  methodValue: { fontSize: 15, color: colors.textSecondary },
  linkRow: {
    borderBottomWidth: 1,
    borderBottomColor: colors.neutral[100],
  },
  linkText: { ...typography.bodyMedium, color: colors.textPrimary },
  linkTextDanger: { color: colors.status.error },
  unavailableText: {
    fontSize: 13,
    color: colors.textTertiary,
    lineHeight: 19,
    marginBottom: spacing.md,
  },
  optionRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    minHeight: 56,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
    borderBottomWidth: 1,
    borderBottomColor: colors.neutral[100],
  },
  optionLabel: { ...typography.bodyMedium, color: colors.textPrimary, flex: 1 },
  pinContent: { flex: 1, justifyContent: 'center', paddingHorizontal: spacing.xl },
  cancelButton: { alignItems: 'center', marginTop: spacing.lg, padding: spacing.sm },
  skipText: { color: colors.textSecondary, fontSize: 14, fontWeight: '500' },
});

import React, { useState } from 'react';
import { View, Text, TouchableOpacity, StyleSheet, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute, type RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import Animated from 'react-native-reanimated';
import * as LocalAuthentication from 'expo-local-authentication';
import { PrimaryButton } from '../components/PrimaryButton';
import { PinEntryPad } from '../components/PinEntryPad';
import {
  setAppLockEnabled,
  setAppLockMethod,
  setPin,
  markAppLockSetupPrompted,
  type AppLockMethod,
} from '../auth/appLock';
import type { RootStackParamList } from '../navigation/RootNavigator';
import { colors, radii, spacing, typography, shadows } from '../theme';
import { Banner, Icon, fadeInUp } from '../components/ui';

type Nav = NativeStackNavigationProp<RootStackParamList, 'AppLockSetupPrompt'>;
type Route = RouteProp<RootStackParamList, 'AppLockSetupPrompt'>;

type Step = 'choose' | 'create-pin' | 'confirm-pin';

/**
 * One-time opt-in prompt shown right after a fresh login (never on cold-start
 * session restore) so the user can choose how — or whether — to lock the app.
 * Always finishes by navigating to the resolved post-auth route so it never
 * blocks getting into the app.
 */
export function AppLockSetupPromptScreen() {
  const navigation = useNavigation<Nav>();
  const route = useRoute<Route>();
  const nextRoute = route.params.nextRoute;

  const [step, setStep] = useState<Step>('choose');
  const [firstPin, setFirstPin] = useState('');
  const [pinError, setPinError] = useState<string | undefined>(undefined);
  const [resetSignal, setResetSignal] = useState(0);
  const [pendingMethod, setPendingMethod] = useState<AppLockMethod | null>(null);

  const finish = async () => {
    await markAppLockSetupPrompted();
    navigation.reset({ index: 0, routes: [nextRoute] });
  };

  const skip = async () => {
    await setAppLockEnabled(false);
    await finish();
  };

  const chooseSystem = async () => {
    const hasHardware = await LocalAuthentication.hasHardwareAsync();
    const isEnrolled = await LocalAuthentication.isEnrolledAsync();
    if (!hasHardware || !isEnrolled) {
      setPinError('No biometrics or device passcode is set up on this device.');
      return;
    }
    await setAppLockMethod('system');
    await setAppLockEnabled(true);
    await finish();
  };

  const choosePin = () => {
    setPendingMethod('pin');
    setPinError(undefined);
    setStep('create-pin');
  };

  const chooseBoth = async () => {
    const hasHardware = await LocalAuthentication.hasHardwareAsync();
    const isEnrolled = await LocalAuthentication.isEnrolledAsync();
    if (!hasHardware || !isEnrolled) {
      setPinError('No biometrics or device passcode is set up on this device.');
      return;
    }
    setPendingMethod('both');
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
    await finish();
  };

  if (step === 'create-pin' || step === 'confirm-pin') {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.pinContent}>
          <Animated.View entering={fadeInUp(60)} style={styles.pinHeader}>
            <Text style={styles.heading}>
              {step === 'create-pin' ? 'Create your 6-digit PIN' : 'Confirm your PIN'}
            </Text>
            <Text style={styles.subheading}>
              {step === 'create-pin'
                ? 'You will use this to unlock PennyRise.'
                : 'Enter the same PIN again.'}
            </Text>
          </Animated.View>
          <Animated.View entering={fadeInUp(120)} style={styles.pinCard}>
            <PinEntryPad
              key={step}
              onComplete={step === 'create-pin' ? onFirstPinComplete : onConfirmPinComplete}
              error={pinError}
              resetSignal={resetSignal}
            />
          </Animated.View>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView
        contentContainerStyle={styles.scroll}
        keyboardShouldPersistTaps="handled"
        showsVerticalScrollIndicator={false}
      >
        <Animated.View entering={fadeInUp(60)} style={styles.heroContent}>
          <View style={styles.iconBadge}>
            <Icon name="lock-closed-outline" size={32} color={colors.gold.text} />
          </View>
          <Text style={styles.heading}>Lock PennyRise?</Text>
          <Text style={styles.subheading}>
            Add an extra layer of protection to your account. You can change this anytime in
            Settings.
          </Text>
        </Animated.View>

        <Animated.View entering={fadeInUp(120)} style={styles.optionsCard}>
          {pinError ? <Banner tone="error" message={pinError} /> : null}

          <PrimaryButton
            title="Use device unlock (Face ID / fingerprint / passcode)"
            onPress={chooseSystem}
            style={styles.option}
          />
          <PrimaryButton title="Use a 6-digit PIN" onPress={choosePin} style={styles.option} />
          <PrimaryButton title="Use both" onPress={chooseBoth} style={styles.option} />

          <TouchableOpacity onPress={skip} style={styles.skipButton}>
            <Text style={styles.skipText}>Skip for now</Text>
          </TouchableOpacity>
        </Animated.View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  scroll: {
    flexGrow: 1,
    justifyContent: 'center',
    paddingHorizontal: spacing.xl,
    paddingTop: spacing['5xl'],
    paddingBottom: spacing['4xl'],
  },
  heroContent: { alignItems: 'center', marginBottom: spacing['2xl'] },
  iconBadge: {
    width: 72,
    height: 72,
    borderRadius: radii.pill,
    backgroundColor: colors.gold.light,
    alignItems: 'center',
    justifyContent: 'center',
    alignSelf: 'center',
    marginBottom: spacing['2xl'],
    ...shadows.sm,
  },
  heading: {
    ...typography.h1,
    color: colors.textPrimary,
    textAlign: 'center',
    marginBottom: spacing.lg,
  },
  subheading: {
    fontSize: 16,
    color: colors.textSecondary,
    textAlign: 'center',
    lineHeight: 24,
  },
  optionsCard: {
    backgroundColor: colors.surface,
    borderRadius: radii['2xl'],
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.xl,
    ...shadows.sm,
  },
  option: { marginBottom: spacing.md },
  skipButton: { alignItems: 'center', marginTop: spacing.lg, padding: spacing.sm },
  skipText: { color: colors.textSecondary, fontSize: 14, fontWeight: '500' },
  pinContent: {
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: spacing.xl,
  },
  pinHeader: { alignItems: 'center', marginBottom: spacing['2xl'] },
  pinCard: {
    backgroundColor: colors.surface,
    borderRadius: radii['2xl'],
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.xl,
    ...shadows.sm,
  },
});

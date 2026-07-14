import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  KeyboardAvoidingView,
  ScrollView,
  Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import Animated from 'react-native-reanimated';

import { GradientHero, Icon, fadeInUp } from '../components/ui';
import type { RootStackParamList } from '../navigation/RootNavigator';
import { FormField } from '../components/FormField';
import { PrimaryButton } from '../components/PrimaryButton';
import { forgotPassword } from '../api/auth';
import { colors, radii, shadows, spacing, typography } from '../theme';

type Nav = NativeStackNavigationProp<RootStackParamList, 'ForgotPassword'>;

const schema = z.object({
  email: z.string().min(1, 'Email is required').email('Invalid email address'),
});
type FormValues = z.infer<typeof schema>;

export default function ForgotPasswordScreen() {
  const navigation = useNavigation<Nav>();
  const [submitted, setSubmitted] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const onSubmit = async (values: FormValues) => {
    if (isSubmitting) return;
    setIsSubmitting(true);
    try {
      await forgotPassword(values.email);
    } catch (err) {
      console.error(err);
      // Show confirmation regardless to prevent enumeration.
    } finally {
      setIsSubmitting(false);
      setSubmitted(true);
    }
  };

  if (submitted) {
    return (
      <SafeAreaView style={styles.safe}>
        <Animated.View entering={fadeInUp(40)} style={styles.confirmedContainer}>
          <View style={styles.iconBadge}>
            <Icon name="mail-outline" size={32} color={colors.gold.text} />
          </View>
          <Text style={styles.heading}>Check your inbox</Text>
          <Text style={styles.body}>
            If an account exists for that email address, we&apos;ve sent a password reset link. The
            link expires in 1 hour.
          </Text>
          <TouchableOpacity
            onPress={() => navigation.navigate('Login')}
            style={styles.backLink}
            hitSlop={8}
          >
            <Text style={styles.backLinkText}>Back to sign in</Text>
          </TouchableOpacity>
        </Animated.View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safe}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        style={styles.flex}
      >
        <ScrollView
          contentContainerStyle={styles.scroll}
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}
        >
          <TouchableOpacity
            onPress={() => navigation.goBack()}
            style={styles.backButton}
            hitSlop={8}
            accessibilityRole="button"
            accessibilityLabel="Go back"
          >
            <Icon name="chevron-back" size={22} color={colors.textPrimary} />
          </TouchableOpacity>

          <Animated.View entering={fadeInUp(40)}>
            <GradientHero
              showLogo
              title="Reset your password"
              subtitle="Enter your email address and we'll send you a reset link if an account exists."
            />
          </Animated.View>

          <Animated.View entering={fadeInUp(110)} style={styles.formCard}>
            <Controller
              control={control}
              name="email"
              render={({ field: { onChange, onBlur, value } }) => (
                <FormField
                  label="Email address"
                  placeholder="you@example.com"
                  value={value}
                  onChangeText={onChange}
                  onBlur={onBlur}
                  error={errors.email?.message}
                  keyboardType="email-address"
                  textContentType="emailAddress"
                />
              )}
            />

            <PrimaryButton
              title="Send reset link"
              onPress={handleSubmit(onSubmit)}
              loading={isSubmitting}
              style={styles.submitButton}
            />
          </Animated.View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  flex: { flex: 1 },
  scroll: {
    paddingHorizontal: spacing.xl,
    paddingTop: spacing.md,
    paddingBottom: spacing['4xl'],
  },
  backButton: {
    width: 40,
    height: 40,
    borderRadius: radii.pill,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.xl,
  },
  formCard: {
    backgroundColor: colors.surface,
    borderRadius: radii['2xl'],
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.xl,
    ...shadows.sm,
  },
  submitButton: { marginTop: spacing.xxs },
  confirmedContainer: {
    flex: 1,
    paddingHorizontal: spacing.xl,
    alignItems: 'center',
    justifyContent: 'center',
  },
  iconBadge: {
    width: 72,
    height: 72,
    borderRadius: radii.pill,
    backgroundColor: colors.gold.light,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing['2xl'],
    ...shadows.sm,
  },
  heading: {
    ...typography.h1,
    color: colors.textPrimary,
    textAlign: 'center',
    marginBottom: spacing.sm,
  },
  body: { fontSize: 16, color: colors.textSecondary, textAlign: 'center', lineHeight: 24 },
  backLink: { marginTop: spacing['3xl'], padding: spacing.md },
  backLinkText: { color: colors.gold.text, fontSize: 15, fontWeight: '700' },
});

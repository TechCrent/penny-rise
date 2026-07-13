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
import { Ionicons } from '@expo/vector-icons';
import Animated, { FadeIn } from 'react-native-reanimated';

import type { RootStackParamList } from '../navigation/RootNavigator';
import { FormField } from '../components/FormField';
import { PrimaryButton } from '../components/PrimaryButton';
import { forgotPassword } from '../api/auth';
import { colors, radii, spacing, typography } from '../theme';

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
        <Animated.View entering={FadeIn.duration(400)} style={styles.confirmedContainer}>
          <View style={styles.iconBadge}>
            <Ionicons name="mail-outline" size={32} color={colors.gold.text} />
          </View>
          <Text style={styles.heading}>Check your inbox</Text>
          <Text style={styles.body}>
            If an account exists for that email address, we&apos;ve sent a password reset link. The
            link expires in 1 hour.
          </Text>
          <TouchableOpacity onPress={() => navigation.navigate('Login')} style={styles.backButton}>
            <Text style={styles.backButtonText}>Back to sign in</Text>
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
        <ScrollView contentContainerStyle={styles.scroll} keyboardShouldPersistTaps="handled">
          <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backRow}>
            <Text style={styles.backText}>← Back</Text>
          </TouchableOpacity>

          <Text style={styles.heading}>Reset your password</Text>
          <Text style={styles.subheading}>
            Enter your email address and we&apos;ll send you a reset link if an account exists.
          </Text>

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
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  flex: { flex: 1 },
  scroll: { paddingHorizontal: spacing.xl, paddingTop: spacing['5xl'], paddingBottom: spacing['4xl'] },
  confirmedContainer: { flex: 1, paddingHorizontal: spacing.xl, paddingTop: 80, alignItems: 'center' },
  iconBadge: {
    width: 72,
    height: 72,
    borderRadius: radii.pill,
    backgroundColor: colors.gold.light,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing['2xl'],
  },
  heading: { ...typography.h1, color: colors.textPrimary, marginBottom: spacing.sm },
  subheading: { fontSize: 16, color: colors.textSecondary, marginBottom: spacing['3xl'] },
  body: { fontSize: 16, color: colors.textSecondary, textAlign: 'center', lineHeight: 24 },
  backRow: { marginBottom: spacing['3xl'] },
  backText: { color: colors.textPrimary, fontSize: 15 },
  submitButton: { marginTop: spacing.sm },
  backButton: { marginTop: spacing['3xl'], padding: spacing.md },
  backButtonText: { color: colors.textPrimary, fontSize: 15, textDecorationLine: 'underline' },
});

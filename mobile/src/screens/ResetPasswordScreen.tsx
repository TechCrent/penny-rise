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
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';

import type { RootStackParamList } from '../navigation/RootNavigator';
import { FormField } from '../components/FormField';
import { PrimaryButton } from '../components/PrimaryButton';
import { resetPassword } from '../api/auth';
import { extractApiError } from '../api/client';

type Nav = NativeStackNavigationProp<RootStackParamList, 'ResetPassword'>;
type Route = RouteProp<RootStackParamList, 'ResetPassword'>;

const schema = z
  .object({
    newPassword: z
      .string()
      .min(8, 'Password must be at least 8 characters')
      .regex(
        /^(?=.*[0-9])(?=.*[!@#$%^&*()_+\-=[\]{};':"\\|,.<>/?]).{8,}$/,
        'Must contain at least one number and one special character',
      ),
    confirmPassword: z.string().min(1, 'Please confirm your new password'),
  })
  .refine(data => data.newPassword === data.confirmPassword, {
    message: 'Passwords do not match',
    path: ['confirmPassword'],
  });

type FormValues = z.infer<typeof schema>;

export default function ResetPasswordScreen() {
  const navigation = useNavigation<Nav>();
  const route = useRoute<Route>();
  const { token } = route.params;

  const [showNew, setShowNew] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [globalError, setGlobalError] = useState<string | null>(null);

  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const onSubmit = async (values: FormValues) => {
    if (isSubmitting) return;
    setIsSubmitting(true);
    setGlobalError(null);

    try {
      await resetPassword(token, values.newPassword);
      navigation.navigate('Login', {
        successBanner: 'Password reset successfully. Please sign in.',
      });
    } catch (error) {
      const apiError = extractApiError(error);

      if (apiError?.code === 'AUTH_RESET_TOKEN_EXPIRED') {
        setGlobalError('This reset link has expired. Please request a new one.');
      } else if (apiError?.code === 'AUTH_RESET_TOKEN_ALREADY_USED') {
        setGlobalError('This reset link has already been used. Please request a new one.');
      } else {
        setGlobalError(apiError?.message ?? 'Something went wrong. Please try again.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <SafeAreaView style={styles.safe}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        style={styles.flex}
      >
        <ScrollView contentContainerStyle={styles.scroll} keyboardShouldPersistTaps="handled">
          <Text style={styles.heading}>Set a new password</Text>
          <Text style={styles.subheading}>
            Choose a strong password with at least one number and one special character.
          </Text>

          {globalError ? (
            <View style={styles.globalError}>
              <Text style={styles.globalErrorText}>{globalError}</Text>
              {globalError.includes('expired') || globalError.includes('already been used') ? (
                <TouchableOpacity onPress={() => navigation.navigate('ForgotPassword')}>
                  <Text style={styles.requestNewLink}>Request a new reset link →</Text>
                </TouchableOpacity>
              ) : null}
            </View>
          ) : null}

          <Controller
            control={control}
            name="newPassword"
            render={({ field: { onChange, onBlur, value } }) => (
              <FormField
                label="New password"
                placeholder="Min. 8 characters"
                value={value}
                onChangeText={onChange}
                onBlur={onBlur}
                error={errors.newPassword?.message}
                secureTextEntry={!showNew}
                textContentType="newPassword"
                rightElement={
                  <TouchableOpacity onPress={() => setShowNew(v => !v)} style={styles.eyeButton}>
                    <Text style={styles.eyeText}>{showNew ? 'Hide' : 'Show'}</Text>
                  </TouchableOpacity>
                }
              />
            )}
          />

          <Controller
            control={control}
            name="confirmPassword"
            render={({ field: { onChange, onBlur, value } }) => (
              <FormField
                label="Confirm new password"
                placeholder="Repeat your new password"
                value={value}
                onChangeText={onChange}
                onBlur={onBlur}
                error={errors.confirmPassword?.message}
                secureTextEntry={!showConfirm}
                textContentType="newPassword"
                rightElement={
                  <TouchableOpacity
                    onPress={() => setShowConfirm(v => !v)}
                    style={styles.eyeButton}
                  >
                    <Text style={styles.eyeText}>{showConfirm ? 'Hide' : 'Show'}</Text>
                  </TouchableOpacity>
                }
              />
            )}
          />

          <PrimaryButton
            title="Set new password"
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
  safe: { flex: 1, backgroundColor: '#FFFFFF' },
  flex: { flex: 1 },
  scroll: { paddingHorizontal: 24, paddingTop: 48, paddingBottom: 40 },
  heading: { fontSize: 28, fontWeight: '700', color: '#111827', marginBottom: 8 },
  subheading: { fontSize: 16, color: '#6B7280', marginBottom: 32 },
  globalError: { backgroundColor: '#FEF2F2', borderRadius: 8, padding: 14, marginBottom: 20 },
  globalErrorText: { color: '#991B1B', fontSize: 14, marginBottom: 8 },
  requestNewLink: {
    color: '#991B1B',
    fontSize: 13,
    fontWeight: '600',
    textDecorationLine: 'underline',
  },
  eyeButton: { paddingHorizontal: 12 },
  eyeText: { color: '#6B7280', fontSize: 14 },
  submitButton: { marginTop: 8 },
});

import React, { useState, useRef } from 'react';
import {
  View,
  Text,
  ScrollView,
  TouchableOpacity,
  StyleSheet,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';

import { type RootStackParamList } from '../navigation/RootNavigator';
import { registerSchema, type RegisterFormValues } from './RegisterScreen.schema';
import { FormField } from '../components/FormField';
import { PrimaryButton } from '../components/PrimaryButton';
import { signup } from '../api/auth';
import { extractApiError } from '../api/client';

type NavigationProp = NativeStackNavigationProp<RootStackParamList, 'Register'>;

export default function RegisterScreen() {
  const navigation = useNavigation<NavigationProp>();
  const [showPassword, setShowPassword] = useState(false);
  const [showReferralField, setShowReferralField] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const submittingRef = useRef(false);

  const {
    control,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { displayName: '', email: '', password: '', referralCode: '' },
  });

  const onSubmit = async (values: RegisterFormValues) => {
    if (submittingRef.current) return;
    submittingRef.current = true;
    setIsSubmitting(true);

    try {
      await signup({
        display_name: values.displayName,
        email: values.email,
        password: values.password,
        referral_code: values.referralCode || undefined,
      });

      navigation.navigate('EmailVerificationPending', { email: values.email });
    } catch (error) {
      const apiError = extractApiError(error);

      if (apiError?.code === 'AUTH_EMAIL_ALREADY_REGISTERED') {
        setError('email', { message: 'An account with this email already exists.' });
      } else if (apiError?.code === 'VALIDATION_ERROR' && apiError.details) {
        for (const [field, message] of Object.entries(apiError.details)) {
          const fieldMap: Record<string, keyof RegisterFormValues> = {
            email: 'email',
            password: 'password',
            display_name: 'displayName',
          };
          const formField = fieldMap[field];
          if (formField) {
            setError(formField, { message: message as string });
          }
        }
      } else {
        setError('email', {
          message: apiError?.message ?? 'Something went wrong. Please try again.',
        });
      }
    } finally {
      submittingRef.current = false;
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
          <Text style={styles.heading}>Create your account</Text>
          <Text style={styles.subheading}>Start saving smarter with Stash.</Text>

          <Controller
            control={control}
            name="displayName"
            render={({ field: { onChange, onBlur, value } }) => (
              <FormField
                label="Display name"
                placeholder="How should we call you?"
                value={value}
                onChangeText={onChange}
                onBlur={onBlur}
                error={errors.displayName?.message}
                returnKeyType="next"
              />
            )}
          />

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
                returnKeyType="next"
              />
            )}
          />

          <Controller
            control={control}
            name="password"
            render={({ field: { onChange, onBlur, value } }) => (
              <FormField
                label="Password"
                placeholder="Min. 8 characters"
                value={value}
                onChangeText={onChange}
                onBlur={onBlur}
                error={errors.password?.message}
                secureTextEntry={!showPassword}
                textContentType="newPassword"
                returnKeyType="done"
                rightElement={
                  <TouchableOpacity
                    onPress={() => setShowPassword(v => !v)}
                    style={styles.eyeButton}
                    hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
                  >
                    <Text style={styles.eyeText}>{showPassword ? 'Hide' : 'Show'}</Text>
                  </TouchableOpacity>
                }
              />
            )}
          />

          {!showReferralField ? (
            <TouchableOpacity
              onPress={() => setShowReferralField(true)}
              style={styles.referralToggle}
            >
              <Text style={styles.referralToggleText}>Have a referral code?</Text>
            </TouchableOpacity>
          ) : (
            <Controller
              control={control}
              name="referralCode"
              render={({ field: { onChange, onBlur, value } }) => (
                <FormField
                  label="Referral code (optional)"
                  placeholder="e.g. FRIEND50"
                  value={value}
                  onChangeText={onChange}
                  onBlur={onBlur}
                  error={errors.referralCode?.message}
                  autoCapitalize="characters"
                  returnKeyType="done"
                />
              )}
            />
          )}

          <PrimaryButton
            title="Create account"
            onPress={handleSubmit(onSubmit)}
            loading={isSubmitting}
            style={styles.submitButton}
          />

          <View style={styles.loginRow}>
            <Text style={styles.loginText}>Already have an account? </Text>
            <TouchableOpacity>
              <Text style={styles.loginLink}>Sign in</Text>
            </TouchableOpacity>
          </View>
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
  eyeButton: { paddingHorizontal: 12 },
  eyeText: { color: '#6B7280', fontSize: 14 },
  referralToggle: { marginBottom: 16 },
  referralToggleText: { color: '#1A1A1A', fontSize: 14, textDecorationLine: 'underline' },
  submitButton: { marginTop: 8 },
  loginRow: { flexDirection: 'row', justifyContent: 'center', marginTop: 24 },
  loginText: { color: '#6B7280', fontSize: 14 },
  loginLink: { color: '#1A1A1A', fontSize: 14, fontWeight: '600' },
});

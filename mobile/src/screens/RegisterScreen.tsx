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
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
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
    defaultValues: {
      displayName: '',
      email: '',
      password: '',
      confirmPassword: '',
      referralCode: '',
      termsAccepted: false,
    },
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
        terms_accepted: values.termsAccepted,
      });

      navigation.navigate('EmailVerificationPending', { email: values.email });
    } catch (error) {
      console.error(error);
      const apiError = extractApiError(error);

      if (apiError?.code === 'AUTH_EMAIL_ALREADY_REGISTERED') {
        setError('email', { message: 'An account with this email already exists.' });
      } else if (apiError?.code === 'VALIDATION_ERROR' && apiError.details) {
        for (const [field, message] of Object.entries(apiError.details)) {
          const fieldMap: Record<string, keyof RegisterFormValues> = {
            email: 'email',
            password: 'password',
            displayName: 'displayName',
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
          <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backRow} hitSlop={8}>
            <Text style={styles.backText}>← Back</Text>
          </TouchableOpacity>

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

          <Controller
            control={control}
            name="confirmPassword"
            render={({ field: { onChange, onBlur, value } }) => (
              <FormField
                label="Confirm password"
                placeholder="Re-enter your password"
                value={value}
                onChangeText={onChange}
                onBlur={onBlur}
                error={errors.confirmPassword?.message}
                secureTextEntry={!showConfirmPassword}
                textContentType="newPassword"
                returnKeyType="done"
                rightElement={
                  <TouchableOpacity
                    onPress={() => setShowConfirmPassword(v => !v)}
                    style={styles.eyeButton}
                    hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
                  >
                    <Text style={styles.eyeText}>{showConfirmPassword ? 'Hide' : 'Show'}</Text>
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

          <Controller
            control={control}
            name="termsAccepted"
            render={({ field: { onChange, value } }) => (
              <View>
                <TouchableOpacity
                  style={styles.termsRow}
                  onPress={() => onChange(!value)}
                  activeOpacity={0.7}
                  accessibilityRole="checkbox"
                  accessibilityState={{ checked: value }}
                >
                  <View style={[styles.checkbox, value ? styles.checkboxChecked : null]}>
                    {value ? <Text style={styles.checkboxMark}>✓</Text> : null}
                  </View>
                  <Text style={styles.termsText}>
                    I agree to the{' '}
                    <Text style={styles.termsLink} onPress={() => navigation.navigate('Legal')}>
                      Terms of Service and Privacy Policy
                    </Text>
                  </Text>
                </TouchableOpacity>
                {errors.termsAccepted ? (
                  <Text style={styles.termsError}>{errors.termsAccepted.message}</Text>
                ) : null}
              </View>
            )}
          />

          <PrimaryButton
            title="Create account"
            onPress={handleSubmit(onSubmit)}
            loading={isSubmitting}
            style={styles.submitButton}
          />

          <View style={styles.loginRow}>
            <Text style={styles.loginText}>Already have an account? </Text>
            <TouchableOpacity onPress={() => navigation.navigate('Login')}>
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
  backRow: { marginBottom: 20 },
  backText: { color: '#1A1A1A', fontSize: 15 },
  heading: { fontSize: 28, fontWeight: '700', color: '#111827', marginBottom: 8 },
  subheading: { fontSize: 16, color: '#6B7280', marginBottom: 32 },
  eyeButton: { paddingHorizontal: 12 },
  eyeText: { color: '#6B7280', fontSize: 14 },
  referralToggle: { marginBottom: 16 },
  referralToggleText: { color: '#1A1A1A', fontSize: 14, textDecorationLine: 'underline' },
  termsRow: { flexDirection: 'row', alignItems: 'flex-start', marginTop: 4, marginBottom: 4 },
  checkbox: {
    width: 20,
    height: 20,
    borderRadius: 4,
    borderWidth: 1.5,
    borderColor: '#D1D5DB',
    marginRight: 10,
    marginTop: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  checkboxChecked: { backgroundColor: '#1A1A1A', borderColor: '#1A1A1A' },
  checkboxMark: { color: '#FFFFFF', fontSize: 13, fontWeight: '700' },
  termsText: { flex: 1, fontSize: 13, color: '#374151', lineHeight: 19 },
  termsLink: { color: '#1A1A1A', fontWeight: '600', textDecorationLine: 'underline' },
  termsError: { color: '#EF4444', fontSize: 12, marginTop: 4, marginLeft: 30 },
  submitButton: { marginTop: 12 },
  loginRow: { flexDirection: 'row', justifyContent: 'center', marginTop: 24 },
  loginText: { color: '#6B7280', fontSize: 14 },
  loginLink: { color: '#1A1A1A', fontSize: 14, fontWeight: '600' },
});

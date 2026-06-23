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

import type { RootStackParamList } from '../navigation/RootNavigator';
import { FormField } from '../components/FormField';
import { PrimaryButton } from '../components/PrimaryButton';
import { forgotPassword } from '../api/auth';

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
    } catch {
      // Show confirmation regardless to prevent enumeration.
    } finally {
      setIsSubmitting(false);
      setSubmitted(true);
    }
  };

  if (submitted) {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.confirmedContainer}>
          <Text style={styles.emoji}>📬</Text>
          <Text style={styles.heading}>Check your inbox</Text>
          <Text style={styles.body}>
            If an account exists for that email address, we&apos;ve sent a password reset link. The
            link expires in 1 hour.
          </Text>
          <TouchableOpacity onPress={() => navigation.navigate('Login')} style={styles.backButton}>
            <Text style={styles.backButtonText}>Back to sign in</Text>
          </TouchableOpacity>
        </View>
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
  safe: { flex: 1, backgroundColor: '#FFFFFF' },
  flex: { flex: 1 },
  scroll: { paddingHorizontal: 24, paddingTop: 48, paddingBottom: 40 },
  confirmedContainer: { flex: 1, paddingHorizontal: 24, paddingTop: 80, alignItems: 'center' },
  emoji: { fontSize: 56, marginBottom: 24 },
  heading: { fontSize: 28, fontWeight: '700', color: '#111827', marginBottom: 8 },
  subheading: { fontSize: 16, color: '#6B7280', marginBottom: 32 },
  body: { fontSize: 16, color: '#6B7280', textAlign: 'center', lineHeight: 24 },
  backRow: { marginBottom: 32 },
  backText: { color: '#1A1A1A', fontSize: 15 },
  submitButton: { marginTop: 8 },
  backButton: { marginTop: 32, padding: 12 },
  backButtonText: { color: '#1A1A1A', fontSize: 15, textDecorationLine: 'underline' },
});

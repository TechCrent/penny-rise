import React, { useState } from 'react';
import {
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
import { z } from 'zod';
import Animated from 'react-native-reanimated';

import type { RootStackParamList } from '../../navigation/RootNavigator';
import { FormField } from '../../components/FormField';
import { PrimaryButton } from '../../components/PrimaryButton';
import { Banner, Icon, ScreenHeader, fadeInUp } from '../../components/ui';
import { changePassword } from '../../api/profileApi';
import { extractApiError } from '../../api/client';
import { colors, radii, shadows, spacing } from '../../theme';

type Nav = NativeStackNavigationProp<RootStackParamList, 'ChangePassword'>;

const schema = z
  .object({
    currentPassword: z.string().min(1, 'Current password is required'),
    newPassword: z
      .string()
      .min(8, 'New password must be at least 8 characters')
      .regex(
        /^(?=.*[0-9])(?=.*[!@#$%^&*()_+\-=[\]{};':"\\|,.<>/?]).{8,}$/,
        'New password must contain at least one number and one special character',
      ),
    confirmPassword: z.string().min(1, 'Please confirm your new password'),
  })
  .refine(values => values.newPassword === values.confirmPassword, {
    message: 'Passwords do not match',
    path: ['confirmPassword'],
  });

type FormValues = z.infer<typeof schema>;

export function ChangePasswordScreen() {
  const navigation = useNavigation<Nav>();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [success, setSuccess] = useState(false);
  const [showCurrent, setShowCurrent] = useState(false);
  const [showNew, setShowNew] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);

  const {
    control,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  });

  const onSubmit = async (values: FormValues) => {
    if (isSubmitting) return;
    setIsSubmitting(true);
    setSuccess(false);
    try {
      await changePassword({
        currentPassword: values.currentPassword,
        newPassword: values.newPassword,
      });
      setSuccess(true);
    } catch (error) {
      const apiError = extractApiError(error);
      if (apiError?.code === 'AUTH_INVALID_CREDENTIALS') {
        setError('currentPassword', { message: 'Current password is incorrect.' });
      } else {
        setError('newPassword', {
          message: apiError?.message ?? 'Something went wrong. Please try again.',
        });
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const renderEyeToggle = (visible: boolean, toggle: () => void) => (
    <TouchableOpacity
      onPress={toggle}
      style={styles.eyeButton}
      hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
      accessibilityRole="button"
      accessibilityLabel={visible ? 'Hide password' : 'Show password'}
    >
      <Icon name={visible ? 'eye-off' : 'eye'} size={20} color={colors.textTertiary} />
    </TouchableOpacity>
  );

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
          <ScreenHeader title="Change password" onBack={() => navigation.goBack()} />

          <Animated.View entering={fadeInUp(60)} style={styles.formCard}>
            {success ? <Banner tone="success" message="Password changed successfully." /> : null}

            <Controller
              control={control}
              name="currentPassword"
              render={({ field: { onChange, onBlur, value } }) => (
                <FormField
                  label="Current password"
                  value={value}
                  onChangeText={onChange}
                  onBlur={onBlur}
                  error={errors.currentPassword?.message}
                  secureTextEntry={!showCurrent}
                  textContentType="password"
                  rightElement={renderEyeToggle(showCurrent, () => setShowCurrent(v => !v))}
                />
              )}
            />

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
                  rightElement={renderEyeToggle(showNew, () => setShowNew(v => !v))}
                />
              )}
            />

            <Controller
              control={control}
              name="confirmPassword"
              render={({ field: { onChange, onBlur, value } }) => (
                <FormField
                  label="Confirm new password"
                  value={value}
                  onChangeText={onChange}
                  onBlur={onBlur}
                  error={errors.confirmPassword?.message}
                  secureTextEntry={!showConfirm}
                  textContentType="newPassword"
                  rightElement={renderEyeToggle(showConfirm, () => setShowConfirm(v => !v))}
                />
              )}
            />

            <PrimaryButton
              title="Update password"
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
  scroll: { paddingHorizontal: spacing.xl, paddingTop: spacing.md, paddingBottom: spacing['4xl'] },
  formCard: {
    backgroundColor: colors.surface,
    borderRadius: radii['2xl'],
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.xl,
    ...shadows.sm,
  },
  eyeButton: { paddingHorizontal: spacing.md },
  submitButton: { marginTop: spacing.sm },
});

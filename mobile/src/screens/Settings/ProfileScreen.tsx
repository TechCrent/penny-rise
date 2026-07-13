import React, { useEffect, useState } from 'react';
import {
  View,
  ScrollView,
  StyleSheet,
  KeyboardAvoidingView,
  Platform,
  ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useForm, Controller } from 'react-hook-form';
import Animated from 'react-native-reanimated';

import type { RootStackParamList } from '../../navigation/RootNavigator';
import { FormField } from '../../components/FormField';
import { PrimaryButton } from '../../components/PrimaryButton';
import { Banner, GradientHero, ScreenHeader, fadeInUp } from '../../components/ui';
import { extractApiError } from '../../api/client';
import { useProfile } from './useProfile';
import { colors, radii, shadows, spacing } from '../../theme';

type Nav = NativeStackNavigationProp<RootStackParamList, 'EditProfile'>;

interface FormValues {
  displayName: string;
  phone: string;
}

export function ProfileScreen() {
  const navigation = useNavigation<Nav>();
  const { profileQuery, update, isUpdating, updateError, updateSuccess } = useProfile();
  const [phoneError, setPhoneError] = useState<string | undefined>(undefined);

  const {
    control,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<FormValues>({ defaultValues: { displayName: '', phone: '' } });

  const profile = profileQuery.data;
  const phoneAlreadySet = !!profile?.phone;

  useEffect(() => {
    if (profile) {
      reset({ displayName: profile.display_name, phone: profile.phone ?? '' });
    }
  }, [profile, reset]);

  useEffect(() => {
    if (updateError) {
      const apiError = extractApiError(updateError);
      if (apiError?.code === 'CONFLICT') {
        setPhoneError('Phone number is already set and cannot be changed.');
      } else {
        setPhoneError(apiError?.message ?? 'Could not update profile. Please try again.');
      }
    }
  }, [updateError]);

  const onSubmit = (values: FormValues) => {
    setPhoneError(undefined);
    update({
      displayName: values.displayName.trim() || undefined,
      phone: phoneAlreadySet ? undefined : values.phone.trim() || undefined,
    });
  };

  if (profileQuery.isLoading) {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.loading}>
          <ActivityIndicator size="large" color={colors.gold.base} />
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
        <ScrollView
          contentContainerStyle={styles.scroll}
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}
        >
          <ScreenHeader title="Profile" onBack={() => navigation.goBack()} />

          <Animated.View entering={fadeInUp(50)}>
            <GradientHero
              icon="person-circle-outline"
              title={profile?.display_name || 'Profile'}
              subtitle={profile?.email ?? ''}
            />
          </Animated.View>

          <Animated.View entering={fadeInUp(110)} style={styles.formCard}>
            <FormField label="Email" value={profile?.email ?? ''} editable={false} />

            <Controller
              control={control}
              name="displayName"
              render={({ field: { onChange, onBlur, value } }) => (
                <FormField
                  label="Display name"
                  value={value}
                  onChangeText={onChange}
                  onBlur={onBlur}
                  error={errors.displayName?.message}
                />
              )}
            />

            <Controller
              control={control}
              name="phone"
              render={({ field: { onChange, onBlur, value } }) => (
                <FormField
                  label={phoneAlreadySet ? 'Phone (locked)' : 'Phone'}
                  placeholder="0501234567"
                  value={value}
                  onChangeText={onChange}
                  onBlur={onBlur}
                  editable={!phoneAlreadySet}
                  keyboardType="phone-pad"
                  error={phoneError}
                />
              )}
            />

            {updateSuccess ? <Banner tone="success" message="Profile updated." /> : null}

            <PrimaryButton
              title="Save changes"
              onPress={handleSubmit(onSubmit)}
              loading={isUpdating}
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
  loading: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  scroll: { paddingHorizontal: spacing.xl, paddingTop: spacing.md, paddingBottom: spacing['4xl'] },
  formCard: {
    backgroundColor: colors.surface,
    borderRadius: radii['2xl'],
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.xl,
    ...shadows.sm,
  },
  submitButton: { marginTop: spacing.sm },
});

import React, { useEffect, useState } from 'react';
import {
  View,
  Text,
  ScrollView,
  TouchableOpacity,
  StyleSheet,
  KeyboardAvoidingView,
  Platform,
  ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useForm, Controller } from 'react-hook-form';

import type { RootStackParamList } from '../../navigation/RootNavigator';
import { FormField } from '../../components/FormField';
import { PrimaryButton } from '../../components/PrimaryButton';
import { extractApiError } from '../../api/client';
import { useProfile } from './useProfile';
import { colors, spacing, typography } from '../../theme';

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
        <ScrollView contentContainerStyle={styles.scroll} keyboardShouldPersistTaps="handled">
          <View style={styles.header}>
            <TouchableOpacity onPress={() => navigation.goBack()} hitSlop={8}>
              <Text style={styles.backText}>← Back</Text>
            </TouchableOpacity>
          </View>

          <Text style={styles.heading}>Profile</Text>

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

          {updateSuccess ? <Text style={styles.successText}>Profile updated.</Text> : null}

          <PrimaryButton
            title="Save changes"
            onPress={handleSubmit(onSubmit)}
            loading={isUpdating}
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
  loading: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  scroll: { paddingHorizontal: spacing.xl, paddingTop: spacing.sm, paddingBottom: spacing['4xl'] },
  header: { marginBottom: spacing['2xl'] },
  backText: { color: colors.textPrimary, fontSize: 15 },
  heading: { ...typography.h1, color: colors.textPrimary, marginBottom: spacing['2xl'] },
  submitButton: { marginTop: spacing.sm },
  successText: { color: colors.status.successText, fontSize: 13, marginBottom: spacing.md },
});

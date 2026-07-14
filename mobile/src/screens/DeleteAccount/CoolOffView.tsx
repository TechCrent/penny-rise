import React from 'react';
import { View, Text, ActivityIndicator, ScrollView, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import Animated from 'react-native-reanimated';
import { Banner, GradientHero, PressableScale, ScreenHeader, fadeInUp } from '../../components/ui';
import { colors, radii, shadows, spacing } from '../../theme';
import type { DeletionRequestStatus } from './types';

interface Props {
  request: DeletionRequestStatus;
  isCancelling: boolean;
  cancelError: unknown;
  onCancel: () => void;
}

export function CoolOffView({ request, isCancelling, cancelError, onCancel }: Props) {
  const navigation = useNavigation();
  const scheduledDate = new Date(request.scheduled_completion_at).toLocaleDateString('en-GH', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  });

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
        <ScreenHeader title="Delete account" onBack={() => navigation.goBack()} />

        <Animated.View entering={fadeInUp(50)}>
          <GradientHero icon="time-outline" title="Your account is scheduled for deletion" />
        </Animated.View>

        <Animated.View entering={fadeInUp(110)} style={styles.card}>
          <Text style={styles.paragraph}>
            Your account will be permanently deleted on{' '}
            <Text style={styles.date}>{scheduledDate}</Text>, unless you cancel before then.
          </Text>

          <Text style={styles.paragraph}>
            Until that date, your account works normally — you can keep using PennyRise as usual.
          </Text>

          {cancelError != null && (
            <Banner
              tone="error"
              message="Something went wrong cancelling your request. Please try again."
              testID="cancel-error"
            />
          )}

          <PressableScale
            style={[styles.cancelButton, isCancelling && styles.disabledButton]}
            disabled={isCancelling}
            onPress={onCancel}
            accessibilityRole="button"
            accessibilityLabel="Cancel deletion request"
          >
            {isCancelling ? (
              <ActivityIndicator color={colors.neutral[0]} testID="cancel-spinner" />
            ) : (
              <Text style={styles.cancelLabel}>Cancel Request</Text>
            )}
          </PressableScale>
        </Animated.View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  scroll: {
    paddingHorizontal: spacing.xl,
    paddingTop: spacing.md,
    paddingBottom: spacing['4xl'],
  },
  card: {
    backgroundColor: colors.surface,
    borderRadius: radii['2xl'],
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.xl,
    ...shadows.sm,
  },
  paragraph: { fontSize: 14, color: colors.textPrimary, marginBottom: spacing.md, lineHeight: 21 },
  date: { fontWeight: '700' },
  cancelButton: {
    backgroundColor: colors.gold.base,
    borderRadius: radii.md,
    paddingVertical: spacing.md,
    alignItems: 'center',
    marginTop: spacing.sm,
  },
  disabledButton: { opacity: 0.6 },
  cancelLabel: { fontSize: 15, fontWeight: '700', color: colors.neutral[900] },
});

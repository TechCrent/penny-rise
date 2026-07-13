import React from 'react';
import { Text, ActivityIndicator, StyleSheet, ViewStyle } from 'react-native';
import { PressableScale } from './ui';
import { colors, radii, shadows, typography } from '../theme';

interface PrimaryButtonProps {
  title: string;
  onPress: () => void;
  loading?: boolean;
  disabled?: boolean;
  style?: ViewStyle;
}

export function PrimaryButton({ title, onPress, loading, disabled, style }: PrimaryButtonProps) {
  const isDisabled = disabled || loading;

  return (
    <PressableScale
      style={[styles.button, isDisabled ? styles.disabled : null, style]}
      onPress={onPress}
      disabled={isDisabled}
      accessibilityRole="button"
      accessibilityLabel={title}
      accessibilityState={{ disabled: isDisabled }}
    >
      {loading ? (
        <ActivityIndicator color={colors.neutral[900]} size="small" />
      ) : (
        <Text style={styles.text}>{title}</Text>
      )}
    </PressableScale>
  );
}

const styles = StyleSheet.create({
  button: {
    backgroundColor: colors.gold.base,
    borderRadius: radii.md,
    paddingVertical: 14,
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 50,
    ...shadows.sm,
  },
  disabled: { backgroundColor: colors.neutral[300] },
  text: { ...typography.button, color: colors.neutral[900] },
});

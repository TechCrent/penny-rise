import React from 'react';
import { View, Text, TextInput, TextInputProps, StyleSheet } from 'react-native';
import Animated, {
  interpolateColor,
  useAnimatedStyle,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { colors, radii, spacing, typography } from '../theme';

interface FormFieldProps extends TextInputProps {
  label: string;
  error?: string;
  rightElement?: React.ReactNode;
}

export function FormField({
  label,
  error,
  rightElement,
  style,
  onFocus,
  onBlur,
  ...inputProps
}: FormFieldProps) {
  const focusProgress = useSharedValue(0);

  const animatedBorderStyle = useAnimatedStyle(() => ({
    borderColor: error
      ? colors.status.error
      : interpolateColor(focusProgress.value, [0, 1], [colors.border, colors.gold.base]),
  }));

  return (
    <View style={styles.container}>
      <Text style={styles.label}>{label}</Text>
      <Animated.View style={[styles.inputRow, animatedBorderStyle]}>
        <TextInput
          style={[styles.input, style]}
          placeholderTextColor={colors.textTertiary}
          autoCapitalize="none"
          onFocus={e => {
            focusProgress.value = withTiming(1, { duration: 150 });
            onFocus?.(e);
          }}
          onBlur={e => {
            focusProgress.value = withTiming(0, { duration: 150 });
            onBlur?.(e);
          }}
          {...inputProps}
        />
        {rightElement}
      </Animated.View>
      {error ? <Text style={styles.errorText}>{error}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { marginBottom: spacing.lg },
  label: {
    ...typography.caption,
    fontWeight: '600',
    color: colors.neutral[700],
    marginBottom: spacing.sm,
  },
  inputRow: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1.5,
    borderColor: colors.border,
    borderRadius: radii.md,
    backgroundColor: colors.surface,
  },
  input: {
    flex: 1,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.md,
    fontSize: 16,
    color: colors.textPrimary,
  },
  errorText: {
    ...typography.caption,
    color: colors.status.error,
    marginTop: spacing.xs,
  },
});

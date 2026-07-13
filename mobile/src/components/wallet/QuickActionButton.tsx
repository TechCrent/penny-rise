import React from 'react';
import { Text, View, StyleSheet } from 'react-native';
import { Icon, PressableScale, type IconName } from '../ui';
import { colors, radii, spacing } from '../../theme';

interface Props {
  icon: IconName;
  label: string;
  onPress: () => void;
  testID?: string;
}

export function QuickActionButton({ icon, label, onPress, testID }: Props) {
  return (
    <PressableScale style={styles.btn} onPress={onPress} testID={testID}>
      <View style={styles.iconCircle}>
        <Icon name={icon} size={20} color={colors.gold.text} />
      </View>
      <Text style={styles.label}>{label}</Text>
    </PressableScale>
  );
}

const styles = StyleSheet.create({
  btn: { alignItems: 'center', flex: 1 },
  iconCircle: {
    width: 52,
    height: 52,
    borderRadius: radii.pill,
    backgroundColor: colors.gold.light,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.sm,
  },
  label: { fontSize: 12, fontWeight: '600', color: colors.neutral[700] },
});

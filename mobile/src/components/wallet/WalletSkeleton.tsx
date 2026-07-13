import React, { useEffect, useRef } from 'react';
import { View, Animated, StyleSheet } from 'react-native';
import { colors, radii, spacing } from '../../theme';

function Bone({
  width,
  height = 16,
  style,
}: {
  width: number | `${number}%`;
  height?: number;
  style?: object;
}) {
  const opacity = useRef(new Animated.Value(0.3)).current;

  useEffect(() => {
    Animated.loop(
      Animated.sequence([
        Animated.timing(opacity, { toValue: 1, duration: 700, useNativeDriver: true }),
        Animated.timing(opacity, { toValue: 0.3, duration: 700, useNativeDriver: true }),
      ]),
    ).start();
  }, [opacity]);

  return <Animated.View style={[styles.bone, { width, height, opacity }, style]} />;
}

export function WalletSkeleton() {
  return (
    <View testID="wallet-skeleton">
      <View style={styles.balanceSection}>
        <Bone width={80} height={12} style={styles.boneOnDark} />
        <Bone width={160} height={40} style={[styles.mt8, styles.boneOnDark]} />
        <Bone width={120} height={12} style={[styles.mt8, styles.boneOnDark]} />
      </View>

      <View style={styles.quickActions}>
        {[1, 2, 3].map(i => (
          <Bone key={i} width={80} height={64} style={styles.actionBone} />
        ))}
      </View>

      {[1, 2, 3, 4, 5].map(i => (
        <View key={i} style={styles.skeletonRow}>
          <Bone width={60} height={24} style={styles.badgeBone} />
          <View style={styles.midSection}>
            <Bone width="70%" height={14} />
            <Bone width="40%" height={11} />
          </View>
          <View style={styles.rightSection}>
            <Bone width={60} height={14} />
            <Bone width={40} height={11} />
          </View>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  bone: {
    backgroundColor: colors.neutral[200],
    borderRadius: radii.sm,
  },
  boneOnDark: { backgroundColor: 'rgba(255,255,255,0.15)' },
  mt8: { marginTop: spacing.sm },
  actionBone: { borderRadius: radii.md },
  badgeBone: { borderRadius: radii.sm },
  midSection: { flex: 1, marginLeft: spacing.md, gap: spacing.xs },
  rightSection: { alignItems: 'flex-end', gap: spacing.xxs },
  balanceSection: {
    backgroundColor: colors.neutral[900],
    padding: spacing.xl,
    paddingTop: spacing['4xl'],
  },
  quickActions: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    padding: spacing.lg,
    backgroundColor: colors.surface,
    gap: spacing.md,
  },
  skeletonRow: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: spacing.lg,
    borderBottomWidth: 1,
    borderBottomColor: colors.neutral[100],
  },
});

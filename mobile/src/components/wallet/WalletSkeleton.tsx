import React, { useEffect, useRef } from 'react';
import { View, Animated, StyleSheet } from 'react-native';

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

  return <Animated.View style={[styles.bone, { width, height }, style]} />;
}

export function WalletSkeleton() {
  return (
    <View testID="wallet-skeleton">
      <View style={styles.balanceSection}>
        <Bone width={80} height={12} />
        <Bone width={160} height={40} style={styles.mt8} />
        <Bone width={120} height={12} style={styles.mt8} />
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
    backgroundColor: '#E5E7EB',
    borderRadius: 8,
  },
  mt8: { marginTop: 8 },
  actionBone: { borderRadius: 12 },
  badgeBone: { borderRadius: 10 },
  midSection: { flex: 1, marginLeft: 12, gap: 6 },
  rightSection: { alignItems: 'flex-end', gap: 4 },
  balanceSection: {
    backgroundColor: '#111827',
    padding: 24,
    paddingTop: 40,
  },
  quickActions: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    padding: 16,
    backgroundColor: '#FFFFFF',
    gap: 12,
  },
  skeletonRow: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#F3F4F6',
  },
});

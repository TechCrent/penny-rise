import React, { useEffect, useRef } from 'react';
import { View, Animated, StyleSheet, Dimensions } from 'react-native';

const { width } = Dimensions.get('window');

interface SkeletonBlockProps {
  blockWidth: number | `${number}%`;
  height: number;
  radius?: number;
  style?: object;
}

function SkeletonBlock({ blockWidth, height, radius = 8, style }: SkeletonBlockProps) {
  const opacity = useRef(new Animated.Value(0.4)).current;

  useEffect(() => {
    const anim = Animated.loop(
      Animated.sequence([
        Animated.timing(opacity, { toValue: 1, duration: 700, useNativeDriver: true }),
        Animated.timing(opacity, { toValue: 0.4, duration: 700, useNativeDriver: true }),
      ]),
    );
    anim.start();
    return () => anim.stop();
  }, [opacity]);

  return (
    <Animated.View
      style={[styles.block, { width: blockWidth, height, borderRadius: radius, opacity }, style]}
    />
  );
}

export function HomeSkeleton() {
  return (
    <View style={styles.container} testID="home-skeleton">
      <SkeletonBlock blockWidth={140} height={22} style={styles.mb8} />
      <SkeletonBlock blockWidth={200} height={16} style={styles.mb24} />

      <SkeletonBlock blockWidth={width - 32} height={100} radius={16} style={styles.mb24} />

      <View style={styles.tileRow}>
        {[0, 1, 2].map(i => (
          <SkeletonBlock key={i} blockWidth={(width - 56) / 3} height={64} radius={12} />
        ))}
      </View>

      <SkeletonBlock blockWidth={120} height={18} style={styles.sectionHeading} />

      <SkeletonBlock blockWidth={width - 32} height={96} radius={14} style={styles.mb12} />
      <SkeletonBlock blockWidth={width - 32} height={96} radius={14} style={styles.mb12} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { paddingHorizontal: 16, paddingTop: 16 },
  block: { backgroundColor: '#E5E7EB' },
  mb8: { marginBottom: 8 },
  mb12: { marginBottom: 12 },
  mb24: { marginBottom: 24 },
  tileRow: { flexDirection: 'row', gap: 12, marginBottom: 28 },
  sectionHeading: { marginBottom: 14 },
});

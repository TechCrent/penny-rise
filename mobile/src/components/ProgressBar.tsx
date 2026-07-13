import React, { useEffect } from 'react';
import { View, StyleSheet } from 'react-native';
import Animated, {
  Easing,
  useAnimatedStyle,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { colors } from '../theme';

interface Props {
  percent: number; // 0-100
}

export function ProgressBar({ percent }: Props) {
  const clamped = Math.max(0, Math.min(100, percent));
  const width = useSharedValue(0);

  useEffect(() => {
    width.value = withTiming(clamped, { duration: 700, easing: Easing.out(Easing.cubic) });
  }, [clamped, width]);

  const animatedStyle = useAnimatedStyle(() => ({ width: `${width.value}%` }));

  return (
    <View
      style={styles.track}
      accessibilityRole="progressbar"
      accessibilityValue={{ min: 0, max: 100, now: clamped }}
    >
      <Animated.View style={[styles.fill, animatedStyle]} />
    </View>
  );
}

const styles = StyleSheet.create({
  track: { height: 6, backgroundColor: colors.neutral[100], borderRadius: 3, overflow: 'hidden' },
  fill: { height: '100%', backgroundColor: colors.gold.base, borderRadius: 3 },
});

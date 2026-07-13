import React, { useEffect } from 'react';
import { View, StyleSheet } from 'react-native';
import type { DimensionValue } from 'react-native';
import Animated, {
  Easing,
  useAnimatedStyle,
  useSharedValue,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';
import { LinearGradient } from 'expo-linear-gradient';
import { colors, radii } from '../../theme';

interface SkeletonProps {
  width: DimensionValue;
  height: number;
  radius?: number;
  style?: object;
}

/**
 * Shimmering placeholder block: a soft gradient sweep looping left→right
 * over a neutral base, used in place of spinners while data loads.
 */
export function Skeleton({ width, height, radius = radii.sm, style }: SkeletonProps) {
  const translateX = useSharedValue(-1);

  useEffect(() => {
    translateX.value = withRepeat(
      withTiming(1, { duration: 1200, easing: Easing.inOut(Easing.ease) }),
      -1,
      false,
    );
  }, [translateX]);

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: translateX.value * SHIMMER_TRAVEL }],
  }));

  return (
    <View style={[{ width, height, borderRadius: radius }, styles.base, style]}>
      <Animated.View style={[StyleSheet.absoluteFill, styles.shimmerWrap, animatedStyle]}>
        <LinearGradient
          colors={['transparent', 'rgba(255,255,255,0.7)', 'transparent']}
          start={{ x: 0, y: 0 }}
          end={{ x: 1, y: 0 }}
          style={StyleSheet.absoluteFill}
        />
      </Animated.View>
    </View>
  );
}

const SHIMMER_TRAVEL = 160;

const styles = StyleSheet.create({
  base: {
    backgroundColor: colors.neutral[200],
    overflow: 'hidden',
  },
  shimmerWrap: {
    width: '100%',
  },
});

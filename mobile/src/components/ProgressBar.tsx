import React from 'react';
import { View, StyleSheet } from 'react-native';

interface Props {
  percent: number; // 0-100
}

export function ProgressBar({ percent }: Props) {
  const clamped = Math.max(0, Math.min(100, percent));
  return (
    <View
      style={styles.track}
      accessibilityRole="progressbar"
      accessibilityValue={{ min: 0, max: 100, now: clamped }}
    >
      <View style={[styles.fill, { width: `${clamped}%` }]} />
    </View>
  );
}

const styles = StyleSheet.create({
  track: { height: 6, backgroundColor: '#F3F4F6', borderRadius: 3, overflow: 'hidden' },
  fill: { height: '100%', backgroundColor: '#1A1A1A', borderRadius: 3 },
});

import React from 'react';
import { View, StyleSheet, Dimensions } from 'react-native';
import { Skeleton } from './ui';
import { radii, spacing } from '../theme';

const { width } = Dimensions.get('window');

export function HomeSkeleton() {
  return (
    <View style={styles.container} testID="home-skeleton">
      <View style={styles.headerRow}>
        <Skeleton width={44} height={44} radius={radii.pill} style={styles.mr12} />
        <View>
          <Skeleton width={140} height={20} style={styles.mb8} />
          <Skeleton width={180} height={14} />
        </View>
      </View>

      <Skeleton width={width - 32} height={44} radius={radii.md} style={styles.mb16} />

      <Skeleton width={width - 32} height={132} radius={radii['2xl']} style={styles.mb24} />

      <View style={styles.tileRow}>
        {[0, 1, 2].map(i => (
          <Skeleton key={i} width={(width - 56) / 3} height={64} radius={radii.lg} />
        ))}
      </View>

      <Skeleton width={120} height={18} style={styles.sectionHeading} />

      <Skeleton width={width - 32} height={96} radius={radii.lg} style={styles.mb12} />
      <Skeleton width={width - 32} height={96} radius={radii.lg} style={styles.mb12} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { paddingHorizontal: spacing.lg, paddingTop: spacing.lg },
  headerRow: { flexDirection: 'row', alignItems: 'center', marginBottom: spacing.xl },
  mr12: { marginRight: spacing.md },
  mb8: { marginBottom: spacing.sm },
  mb12: { marginBottom: spacing.md },
  mb16: { marginBottom: spacing.lg },
  mb24: { marginBottom: spacing['2xl'] },
  tileRow: { flexDirection: 'row', gap: spacing.md, marginBottom: spacing['3xl'] },
  sectionHeading: { marginBottom: spacing.md },
});

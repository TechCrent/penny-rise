import React from 'react';
import { View, StyleSheet } from 'react-native';
import { Skeleton } from '../../../components/ui';
import { colors, radii, spacing } from '../../../theme';

function Row() {
  return (
    <View style={styles.row}>
      <Skeleton width={36} height={36} radius={radii.pill} />
      <View style={styles.midSection}>
        <Skeleton width="55%" height={13} />
        <Skeleton width="35%" height={11} style={styles.mt} />
      </View>
      <Skeleton width={56} height={13} />
    </View>
  );
}

/** Shimmering row placeholders shown while the first page of history loads. */
export function TransactionHistorySkeleton() {
  return (
    <View testID="history-skeleton">
      {[1, 2, 3, 4, 5, 6].map(i => (
        <Row key={i} />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border,
  },
  midSection: { flex: 1, marginLeft: spacing.md, gap: spacing.xs },
  mt: { marginTop: spacing.xxs },
});

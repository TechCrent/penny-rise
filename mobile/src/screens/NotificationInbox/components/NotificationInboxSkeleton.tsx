import React from 'react';
import { View, StyleSheet } from 'react-native';
import { Skeleton } from '../../../components/ui';
import { spacing } from '../../../theme';

function Row() {
  return (
    <View style={styles.row}>
      <View style={styles.textContainer}>
        <Skeleton width="60%" height={14} />
        <Skeleton width="90%" height={12} style={styles.mt} />
      </View>
    </View>
  );
}

/** Shimmering row placeholders shown while the notification inbox loads. */
export function NotificationInboxSkeleton() {
  return (
    <View testID="inbox-skeleton">
      {[1, 2, 3, 4, 5, 6].map(i => (
        <Row key={i} />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.lg,
  },
  textContainer: { flex: 1, gap: spacing.xs },
  mt: { marginTop: spacing.xxs },
});

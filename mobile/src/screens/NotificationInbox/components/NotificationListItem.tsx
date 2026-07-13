import React from 'react';
import { Pressable, View, Text, StyleSheet } from 'react-native';
import { colors, radii, spacing } from '../../../theme';
import type { NotificationItem } from '../../../features/notifications/types';

interface Props {
  notification: NotificationItem;
  onPress: (notification: NotificationItem) => void;
}

export function NotificationListItem({ notification, onPress }: Props) {
  const isUnread = notification.read_at === null;

  return (
    <Pressable
      onPress={() => onPress(notification)}
      accessibilityRole="button"
      accessibilityLabel={`${notification.title}${isUnread ? ', unread' : ''}`}
      style={[styles.row, isUnread && styles.rowUnread]}
      testID={`notification-row-${notification.id}`}
    >
      {isUnread && <View style={styles.unreadDot} testID="unread-indicator" />}
      <View style={styles.textContainer}>
        <Text style={[styles.title, isUnread && styles.titleUnread]}>{notification.title}</Text>
        <Text style={styles.body} numberOfLines={2}>
          {notification.body}
        </Text>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.lg,
    alignItems: 'flex-start',
  },
  rowUnread: { backgroundColor: colors.gold.light },
  unreadDot: {
    width: 8,
    height: 8,
    borderRadius: radii.pill,
    backgroundColor: colors.gold.base,
    marginTop: 6,
    marginRight: spacing.sm,
  },
  textContainer: { flex: 1 },
  title: { fontSize: 15, color: colors.textPrimary },
  titleUnread: { fontWeight: '700' },
  body: { fontSize: 13, color: colors.textSecondary, marginTop: 2 },
});

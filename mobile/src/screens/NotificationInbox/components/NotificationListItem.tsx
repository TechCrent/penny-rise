import React from 'react';
import { Pressable, View, Text, StyleSheet } from 'react-native';
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
      style={styles.row}
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
    paddingVertical: 14,
    paddingHorizontal: 16,
    alignItems: 'flex-start',
  },
  unreadDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: '#1A1A1A',
    marginTop: 6,
    marginRight: 8,
  },
  textContainer: { flex: 1 },
  title: { fontSize: 15, color: '#111827' },
  titleUnread: { fontWeight: '700' },
  body: { fontSize: 13, color: '#6B7280', marginTop: 2 },
});

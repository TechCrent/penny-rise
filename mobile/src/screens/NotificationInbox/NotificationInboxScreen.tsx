import React, { useCallback } from 'react';
import {
  FlatList,
  View,
  Text,
  Pressable,
  ActivityIndicator,
  StyleSheet,
  RefreshControl,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useNotificationsList } from '../../features/notifications/useNotificationsList';
import { navigateForNotification } from '../../features/notifications/deepLinkRouter';
import { NotificationListItem } from './components/NotificationListItem';
import { NotificationEmptyState } from './components/NotificationEmptyState';
import type { NotificationItem } from '../../features/notifications/types';
import type { RootStackParamList } from '../../navigation/RootNavigator';

type Nav = NativeStackNavigationProp<RootStackParamList, 'Notifications'>;

export function NotificationInboxScreen() {
  const navigation = useNavigation<Nav>();
  const {
    notifications,
    unreadCount,
    isLoading,
    hasNextPage,
    isFetchingNextPage,
    fetchNextPage,
    isRefetching,
    refetch,
    markRead,
    markAllRead,
  } = useNotificationsList();

  const handlePress = useCallback(
    (notification: NotificationItem) => {
      if (notification.read_at === null) {
        markRead(notification.id);
      }
      navigateForNotification(navigation, notification.type, notification.data);
    },
    [markRead, navigation],
  );

  const handleLoadMore = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage) {
      fetchNextPage();
    }
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  if (isLoading) {
    return (
      <View style={styles.centered}>
        <ActivityIndicator testID="inbox-loading" />
      </View>
    );
  }

  return (
    <View style={styles.container} testID="notification-inbox-screen">
      {unreadCount > 0 && (
        <View style={styles.header}>
          <Text style={styles.headerText}>{unreadCount} unread</Text>
          <Pressable
            onPress={() => markAllRead()}
            accessibilityRole="button"
            accessibilityLabel="Mark all as read"
          >
            <Text style={styles.markAllText}>Mark all as read</Text>
          </Pressable>
        </View>
      )}

      <FlatList
        data={notifications}
        keyExtractor={item => item.id}
        renderItem={({ item }) => (
          <NotificationListItem notification={item} onPress={handlePress} />
        )}
        ListEmptyComponent={<NotificationEmptyState />}
        onEndReached={handleLoadMore}
        onEndReachedThreshold={0.3}
        refreshControl={<RefreshControl refreshing={isRefetching} onRefresh={refetch} />}
        ListFooterComponent={
          isFetchingNextPage ? (
            <ActivityIndicator style={styles.footerSpinner} testID="load-more-spinner" />
          ) : null
        }
        testID="notification-list"
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F9FAFB' },
  centered: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  headerText: { color: '#6B7280' },
  markAllText: { color: '#1A1A1A', fontWeight: '600' },
  footerSpinner: { paddingVertical: 16 },
});

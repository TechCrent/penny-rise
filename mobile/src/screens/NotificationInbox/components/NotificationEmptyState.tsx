import React from 'react';
import { View, Text, StyleSheet } from 'react-native';

export function NotificationEmptyState() {
  return (
    <View style={styles.container} testID="empty-state">
      <Text style={styles.icon}>📭</Text>
      <Text style={styles.title}>You&apos;re all caught up</Text>
      <Text style={styles.subtitle}>
        Notifications about deposits, transfers, and challenges will show up here.
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 32 },
  icon: { fontSize: 48, marginBottom: 12 },
  title: {
    fontSize: 18,
    fontWeight: '700',
    color: '#111827',
    textAlign: 'center',
    marginBottom: 8,
  },
  subtitle: { fontSize: 14, color: '#6B7280', textAlign: 'center' },
});

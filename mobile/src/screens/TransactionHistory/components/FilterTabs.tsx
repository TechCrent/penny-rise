import React from 'react';
import { ScrollView, Pressable, Text, StyleSheet } from 'react-native';
import type { FilterTab } from '../types';

const TABS: { key: FilterTab; label: string }[] = [
  { key: 'ALL', label: 'All' },
  { key: 'DEPOSIT', label: 'Deposits' },
  { key: 'WITHDRAWAL', label: 'Withdrawals' },
  { key: 'TRANSFER', label: 'Transfers' },
  { key: 'SUSU', label: 'Susu' },
];

interface Props {
  activeTab: FilterTab;
  onTabChange: (tab: FilterTab) => void;
}

export function FilterTabs({ activeTab, onTabChange }: Props) {
  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      style={styles.container}
      contentContainerStyle={styles.content}
    >
      {TABS.map(({ key, label }) => {
        const isActive = key === activeTab;
        return (
          <Pressable
            key={key}
            onPress={() => onTabChange(key)}
            style={[styles.tab, isActive && styles.activeTab]}
            accessibilityRole="tab"
            accessibilityState={{ selected: isActive }}
            accessibilityLabel={`${label} filter tab`}
          >
            <Text style={[styles.tabLabel, isActive && styles.activeTabLabel]}>{label}</Text>
          </Pressable>
        );
      })}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { maxHeight: 48, flexGrow: 0 },
  content: { paddingHorizontal: 16, gap: 8 },
  tab: {
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 16,
    backgroundColor: '#F3F4F6',
  },
  activeTab: { backgroundColor: '#1A1A1A' },
  tabLabel: { fontSize: 13, color: '#6B7280', fontWeight: '500' },
  activeTabLabel: { color: '#FFFFFF', fontWeight: '700' },
});

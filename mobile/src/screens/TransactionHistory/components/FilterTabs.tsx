import React from 'react';
import { ScrollView, Pressable, Text, StyleSheet } from 'react-native';
import { colors, radii, spacing } from '../../../theme';
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
  content: { paddingHorizontal: spacing.lg, gap: spacing.sm },
  tab: {
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    borderRadius: radii.pill,
    backgroundColor: colors.neutral[100],
  },
  activeTab: { backgroundColor: colors.gold.base },
  tabLabel: { fontSize: 13, color: colors.textSecondary, fontWeight: '500' },
  activeTabLabel: { color: colors.neutral[900], fontWeight: '700' },
});

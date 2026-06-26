import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';

type VaultType = 'STANDARD' | 'LOCKED';

interface VaultTypeCardProps {
  type: VaultType;
  selected: boolean;
  onSelect: () => void;
  disabled?: boolean;
  limitReached?: boolean;
}

const TYPE_META = {
  STANDARD: {
    icon: '🏦',
    title: 'Standard vault',
    desc: 'Save freely. Deposit and withdraw anytime with no restrictions.',
    note: null,
  },
  LOCKED: {
    icon: '🔒',
    title: 'Locked vault',
    desc: 'Commit to a goal. Lock your savings until a date or amount target is reached.',
    note: 'Early exit incurs a 5% penalty on the balance at the time of exit.',
  },
} as const;

export function VaultTypeCard({ type, selected, onSelect, disabled, limitReached }: VaultTypeCardProps) {
  const meta = TYPE_META[type];

  return (
    <TouchableOpacity
      style={[
        styles.card,
        selected && styles.cardSelected,
        disabled && styles.cardDisabled,
      ]}
      onPress={onSelect}
      disabled={disabled}
      activeOpacity={0.82}
      accessibilityRole="radio"
      accessibilityState={{ selected, disabled }}
      accessibilityLabel={`${meta.title}. ${meta.desc}`}
    >
      <View style={styles.topRow}>
        <Text style={styles.icon}>{meta.icon}</Text>
        <View style={styles.radioOuter}>
          {selected && <View style={styles.radioInner} />}
        </View>
      </View>

      <Text style={[styles.title, disabled && styles.textDisabled]}>{meta.title}</Text>
      <Text style={[styles.desc, disabled && styles.textDisabled]}>{meta.desc}</Text>

      {type === 'LOCKED' && meta.note && (
        <View style={styles.penaltyRow}>
          <Text style={styles.penaltyIcon}>⚠️</Text>
          <Text style={styles.penaltyText}>{meta.note}</Text>
        </View>
      )}

      {limitReached && (
        <View style={styles.limitBanner}>
          <Text style={styles.limitText}>
            {type === 'STANDARD'
              ? 'Free plan: 2 standard vaults maximum'
              : 'Free plan: 1 locked vault maximum'}
          </Text>
        </View>
      )}
    </TouchableOpacity>
  );
}

const INDIGO = '#4F46E5';
const DARK   = '#1A1A2E';
const MUTED  = '#6B7280';
const AMBER  = '#D97706';

const styles = StyleSheet.create({
  card: {
    backgroundColor: '#FFFFFF',
    borderRadius: 16,
    padding: 18,
    marginBottom: 12,
    borderWidth: 2,
    borderColor: '#EDEDF0',
  },
  cardSelected: {
    borderColor: INDIGO,
    backgroundColor: '#F5F3FF',
  },
  cardDisabled: {
    opacity: 0.55,
    backgroundColor: '#F9FAFB',
  },
  topRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  icon: { fontSize: 28 },
  radioOuter: {
    width: 22,
    height: 22,
    borderRadius: 11,
    borderWidth: 2,
    borderColor: INDIGO,
    alignItems: 'center',
    justifyContent: 'center',
  },
  radioInner: {
    width: 11,
    height: 11,
    borderRadius: 6,
    backgroundColor: INDIGO,
  },
  title: {
    fontSize: 15,
    fontWeight: '700',
    color: DARK,
    marginBottom: 4,
  },
  desc: {
    fontSize: 13,
    color: MUTED,
    lineHeight: 19,
  },
  textDisabled: {
    color: '#9CA3AF',
  },
  penaltyRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 6,
    marginTop: 10,
    backgroundColor: '#FEF3C7',
    borderRadius: 8,
    padding: 10,
  },
  penaltyIcon: { fontSize: 13, lineHeight: 18 },
  penaltyText: {
    flex: 1,
    fontSize: 12,
    color: AMBER,
    fontWeight: '600',
    lineHeight: 17,
  },
  limitBanner: {
    marginTop: 10,
    backgroundColor: '#FEF2F2',
    borderRadius: 8,
    padding: 8,
  },
  limitText: {
    fontSize: 12,
    color: '#991B1B',
    fontWeight: '600',
  },
});

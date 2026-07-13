import React, { useState, useEffect, useRef, useCallback } from 'react';
import { View, Text, TextInput, FlatList, ActivityIndicator, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../../navigation/RootNavigator';
import { transferApi } from '../../api/transfers';
import type { RecipientResult } from '../../api/transfers';
import { EmptyState, Icon, PressableScale, ScreenHeader } from '../../components/ui';
import { colors, radii, shadows, spacing, typography } from '../../theme';

type Nav = NativeStackNavigationProp<RootStackParamList, 'RecipientPicker'>;

function useDebounce<T>(value: T, ms: number): T {
  const [debounced, setDebounced] = useState<T>(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return debounced;
}

function initialOf(name: string): string {
  return name.trim().charAt(0).toUpperCase() || '?';
}

export function RecipientPickerScreen() {
  const navigation = useNavigation<Nav>();
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<RecipientResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const debouncedQuery = useDebounce(query, 300);
  const controllerRef = useRef<AbortController | null>(null);

  useEffect(() => {
    if (debouncedQuery.trim().length < 2) {
      setResults([]);
      return;
    }
    controllerRef.current?.abort();
    controllerRef.current = new AbortController();
    setLoading(true);
    setError(null);
    transferApi
      .searchRecipients(debouncedQuery.trim())
      .then(data => setResults(data))
      .catch(e => {
        console.error(e);
        const err = e as { message?: string } | null;
        if (err?.message !== 'canceled') {
          setError('Could not load results.');
        }
      })
      .finally(() => setLoading(false));
  }, [debouncedQuery]);

  const handleSelect = useCallback(
    (r: RecipientResult) => {
      navigation.navigate('SendMoney', { recipient: r });
    },
    [navigation],
  );

  return (
    <SafeAreaView style={styles.screen}>
      <View style={styles.header}>
        <ScreenHeader title="Send money" onBack={() => navigation.goBack()} />
      </View>

      <View style={styles.searchRow}>
        <Icon name="people-outline" size={18} color={colors.textTertiary} />
        <TextInput
          style={styles.searchInput}
          placeholder="Search by name or email"
          placeholderTextColor={colors.textTertiary}
          value={query}
          onChangeText={setQuery}
          autoFocus
          testID="search-input"
        />
        {loading && <ActivityIndicator style={styles.searchSpinner} color={colors.gold.base} />}
      </View>

      {error !== null && (
        <Text style={styles.errorText} testID="search-error">
          {error}
        </Text>
      )}

      <FlatList
        data={results}
        keyExtractor={r => r.id}
        testID="results-list"
        contentContainerStyle={styles.listContent}
        keyboardShouldPersistTaps="handled"
        renderItem={({ item: r }) => (
          <PressableScale
            style={styles.resultRow}
            onPress={() => handleSelect(r)}
            testID={`recipient-${r.id}`}
          >
            <View style={styles.avatar}>
              <Text style={styles.avatarText}>{initialOf(r.displayName)}</Text>
            </View>
            <View style={styles.resultBody}>
              <Text style={styles.resultName} numberOfLines={1}>
                {r.displayName}
              </Text>
              <Text style={styles.resultEmail} numberOfLines={1}>
                {r.email}
              </Text>
            </View>
            <Icon name="arrow-forward" size={18} color={colors.textTertiary} />
          </PressableScale>
        )}
        ListEmptyComponent={
          debouncedQuery.trim().length >= 2 && !loading ? (
            <EmptyState
              icon="people-outline"
              title="No results found."
              message="Try a different name or email address."
              testID="no-results"
            />
          ) : null
        }
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background },
  header: { paddingHorizontal: spacing.xl, paddingTop: spacing.md },
  searchRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
    marginHorizontal: spacing.xl,
    marginBottom: spacing.md,
    backgroundColor: colors.surface,
    borderRadius: radii.lg,
    borderWidth: 1,
    borderColor: colors.border,
    paddingHorizontal: spacing.lg,
    ...shadows.sm,
  },
  searchInput: { flex: 1, height: 52, ...typography.body, color: colors.textPrimary },
  searchSpinner: { marginLeft: spacing.sm },
  listContent: { paddingHorizontal: spacing.xl, paddingBottom: spacing['4xl'] },
  resultRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    backgroundColor: colors.surface,
    borderRadius: radii.lg,
    borderWidth: 1,
    borderColor: colors.border,
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.lg,
    marginBottom: spacing.sm,
    ...shadows.sm,
  },
  avatar: {
    width: 44,
    height: 44,
    borderRadius: radii.pill,
    backgroundColor: colors.gold.light,
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarText: { ...typography.bodyMedium, color: colors.gold.text, fontWeight: '700' },
  resultBody: { flex: 1 },
  resultName: { ...typography.bodyMedium, color: colors.textPrimary },
  resultEmail: { ...typography.caption, color: colors.textSecondary, marginTop: 2 },
  errorText: {
    color: colors.status.error,
    textAlign: 'center',
    marginBottom: spacing.sm,
    ...typography.caption,
  },
});

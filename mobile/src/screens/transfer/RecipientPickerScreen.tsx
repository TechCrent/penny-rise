import React, { useState, useEffect, useRef, useCallback } from 'react';
import { View, Text, TextInput, FlatList, ActivityIndicator, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../../navigation/RootNavigator';
import { transferApi } from '../../api/transfers';
import type { RecipientResult } from '../../api/transfers';
import { PressableScale } from '../../components/ui';
import { colors, radii, spacing } from '../../theme';

type Nav = NativeStackNavigationProp<RootStackParamList, 'RecipientPicker'>;

function useDebounce<T>(value: T, ms: number): T {
  const [debounced, setDebounced] = useState<T>(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return debounced;
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
      <View style={styles.searchRow}>
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
        renderItem={({ item: r }) => (
          <PressableScale
            style={styles.resultRow}
            onPress={() => handleSelect(r)}
            testID={`recipient-${r.id}`}
          >
            <Text style={styles.resultName}>{r.displayName}</Text>
            <Text style={styles.resultEmail}>{r.email}</Text>
          </PressableScale>
        )}
        ListEmptyComponent={
          debouncedQuery.trim().length >= 2 && !loading ? (
            <Text style={styles.emptyText} testID="no-results">
              No results found.
            </Text>
          ) : null
        }
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background },
  searchRow: {
    flexDirection: 'row',
    alignItems: 'center',
    margin: spacing.lg,
    backgroundColor: colors.surface,
    borderRadius: radii.md,
    borderWidth: 1.5,
    borderColor: colors.border,
    paddingHorizontal: spacing.md,
  },
  searchInput: { flex: 1, height: 48, fontSize: 16, color: colors.textPrimary },
  searchSpinner: { marginLeft: spacing.sm },
  resultRow: {
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.xl,
    borderBottomWidth: 1,
    borderBottomColor: colors.neutral[100],
  },
  resultName: { fontSize: 15, fontWeight: '600', color: colors.textPrimary },
  resultEmail: { fontSize: 13, color: colors.textSecondary, marginTop: 2 },
  emptyText: { textAlign: 'center', color: colors.textSecondary, marginTop: spacing['4xl'] },
  errorText: { color: colors.status.error, textAlign: 'center', marginBottom: spacing.sm },
});

import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  View,
  Text,
  TextInput,
  FlatList,
  TouchableOpacity,
  ActivityIndicator,
  StyleSheet,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../../navigation/RootNavigator';
import { transferApi } from '../../api/transfers';
import type { RecipientResult } from '../../api/transfers';

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
          value={query}
          onChangeText={setQuery}
          autoFocus
          testID="search-input"
        />
        {loading && <ActivityIndicator style={styles.searchSpinner} />}
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
          <TouchableOpacity
            style={styles.resultRow}
            onPress={() => handleSelect(r)}
            testID={`recipient-${r.id}`}
          >
            <Text style={styles.resultName}>{r.displayName}</Text>
            <Text style={styles.resultEmail}>{r.email}</Text>
          </TouchableOpacity>
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
  screen: { flex: 1, backgroundColor: '#F9FAFB' },
  searchRow: {
    flexDirection: 'row',
    alignItems: 'center',
    margin: 16,
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    borderWidth: 1.5,
    borderColor: '#E5E7EB',
    paddingHorizontal: 14,
  },
  searchInput: { flex: 1, height: 48, fontSize: 16, color: '#111827' },
  searchSpinner: { marginLeft: 8 },
  resultRow: {
    paddingVertical: 14,
    paddingHorizontal: 20,
    borderBottomWidth: 1,
    borderBottomColor: '#F3F4F6',
  },
  resultName: { fontSize: 15, fontWeight: '600', color: '#111827' },
  resultEmail: { fontSize: 13, color: '#6B7280', marginTop: 2 },
  emptyText: { textAlign: 'center', color: '#6B7280', marginTop: 40 },
  errorText: { color: '#EF4444', textAlign: 'center', marginBottom: 8 },
});

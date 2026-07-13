import React, { useMemo } from 'react';
import { SectionList, ActivityIndicator, View, Text, StyleSheet } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useChallenges } from './useChallenges';
import { ChallengeCard } from './components/ChallengeCard';
import { sectionFor } from './types';
import type { Challenge, ChallengeSection } from './types';
import type { RootStackParamList } from '../../navigation/RootNavigator';
import { colors, spacing } from '../../theme';

type Nav = NativeStackNavigationProp<RootStackParamList, 'ChallengesList'>;

const SECTION_TITLES: Record<ChallengeSection, string> = {
  ACTIVE: 'Active',
  AVAILABLE: 'Available',
  COMPLETED: 'Completed',
};
const SECTION_ORDER: ChallengeSection[] = ['ACTIVE', 'AVAILABLE', 'COMPLETED'];

export function ChallengesListScreen() {
  const navigation = useNavigation<Nav>();
  const { data: challenges, isLoading, isError } = useChallenges();

  const sections = useMemo(() => {
    if (!challenges) return [];
    const grouped: Record<ChallengeSection, Challenge[]> = {
      ACTIVE: [],
      AVAILABLE: [],
      COMPLETED: [],
    };
    for (const c of challenges) grouped[sectionFor(c)].push(c);
    return SECTION_ORDER.filter(key => grouped[key].length > 0).map(key => ({
      title: SECTION_TITLES[key],
      data: grouped[key],
    }));
  }, [challenges]);

  if (isLoading) {
    return (
      <View style={styles.centered}>
        <ActivityIndicator testID="challenges-loading" color={colors.gold.base} />
      </View>
    );
  }

  if (isError) {
    return (
      <View style={styles.centered}>
        <Text style={styles.message}>Couldn&apos;t load challenges. Pull down to try again.</Text>
      </View>
    );
  }

  return (
    <SectionList
      style={styles.list}
      sections={sections}
      keyExtractor={item => item.id}
      renderItem={({ item }) => (
        <ChallengeCard
          challenge={item}
          onPress={c => navigation.navigate('ChallengeDetail', { challengeId: c.id })}
        />
      )}
      renderSectionHeader={({ section: { title } }) => (
        <Text style={styles.sectionHeader}>{title}</Text>
      )}
      ListEmptyComponent={
        <View style={styles.centered}>
          <Text style={styles.message}>No challenges available right now.</Text>
        </View>
      }
      contentContainerStyle={styles.content}
      testID="challenges-list"
    />
  );
}

const styles = StyleSheet.create({
  list: { flex: 1, backgroundColor: colors.background },
  content: { paddingVertical: spacing.md },
  centered: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing['3xl'] },
  message: { fontSize: 14, color: colors.textSecondary, textAlign: 'center' },
  sectionHeader: {
    fontSize: 15,
    fontWeight: '700',
    color: colors.textPrimary,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.sm,
    backgroundColor: colors.background,
  },
});

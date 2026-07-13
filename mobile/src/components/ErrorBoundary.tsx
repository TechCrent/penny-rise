import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { PressableScale } from './ui';
import { colors, radii, spacing, typography } from '../theme';

interface Props {
  children: React.ReactNode;
}

interface State {
  error: Error | null;
}

/**
 * Catches render-time exceptions anywhere in the tree below it. React error
 * boundaries must be class components — there is no hook equivalent.
 * Without this, an uncaught throw in any screen crashes the whole app with
 * no recovery path.
 */
export class ErrorBoundary extends React.Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    console.error('Unhandled render error', error, info.componentStack);
  }

  reset = () => this.setState({ error: null });

  render() {
    if (this.state.error) {
      return (
        <SafeAreaView style={styles.safe}>
          <View style={styles.content}>
            <View style={styles.iconBadge}>
              <Ionicons name="warning-outline" size={28} color={colors.status.error} />
            </View>
            <Text style={styles.heading}>Something went wrong</Text>
            <Text style={styles.body}>
              Stash ran into an unexpected error. Try again, and if it keeps happening, restart the
              app.
            </Text>
            <PressableScale style={styles.button} onPress={this.reset}>
              <Text style={styles.buttonText}>Try again</Text>
            </PressableScale>
          </View>
        </SafeAreaView>
      );
    }

    return this.props.children;
  }
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  content: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing['3xl'] },
  iconBadge: {
    width: 64,
    height: 64,
    borderRadius: radii.pill,
    backgroundColor: colors.status.errorBg,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.xl,
  },
  heading: { ...typography.h2, fontSize: 20, color: colors.textPrimary, marginBottom: spacing.sm },
  body: {
    fontSize: 14,
    color: colors.textSecondary,
    textAlign: 'center',
    lineHeight: 20,
    marginBottom: spacing['2xl'],
  },
  button: {
    backgroundColor: colors.gold.base,
    borderRadius: radii.md,
    paddingHorizontal: spacing['2xl'],
    paddingVertical: spacing.md,
  },
  buttonText: { color: colors.neutral[900], fontSize: 15, fontWeight: '700' },
});

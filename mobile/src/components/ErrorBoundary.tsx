import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

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
            <Text style={styles.emoji}>⚠️</Text>
            <Text style={styles.heading}>Something went wrong</Text>
            <Text style={styles.body}>
              Stash ran into an unexpected error. Try again, and if it keeps happening, restart the
              app.
            </Text>
            <TouchableOpacity style={styles.button} onPress={this.reset} activeOpacity={0.85}>
              <Text style={styles.buttonText}>Try again</Text>
            </TouchableOpacity>
          </View>
        </SafeAreaView>
      );
    }

    return this.props.children;
  }
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#FFFFFF' },
  content: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 32 },
  emoji: { fontSize: 48, marginBottom: 20 },
  heading: { fontSize: 20, fontWeight: '700', color: '#111827', marginBottom: 8 },
  body: {
    fontSize: 14,
    color: '#6B7280',
    textAlign: 'center',
    lineHeight: 20,
    marginBottom: 28,
  },
  button: {
    backgroundColor: '#1A1A1A',
    borderRadius: 10,
    paddingHorizontal: 32,
    paddingVertical: 14,
  },
  buttonText: { color: '#FFFFFF', fontSize: 15, fontWeight: '700' },
});

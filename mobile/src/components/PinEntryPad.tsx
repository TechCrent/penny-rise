import React, { useState } from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { PressableScale } from './ui';
import { colors, radii, spacing } from '../theme';

const PIN_LENGTH = 6;
const KEYS = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '', '0', 'del'];

interface PinEntryPadProps {
  onComplete: (pin: string) => void;
  error?: string;
  /** Bump this to force the pad to clear its digits (e.g. after a wrong PIN). */
  resetSignal?: number;
}

export function PinEntryPad({ onComplete, error, resetSignal }: PinEntryPadProps) {
  const [value, setValue] = useState('');

  React.useEffect(() => {
    setValue('');
  }, [resetSignal]);

  // Reading the completed PIN off `value` via an effect (rather than off the
  // `next` local inside onKeyPress) avoids a stale-closure bug: consecutive
  // presses handled in the same React batch would otherwise each compute
  // `next` from the same pre-batch `value`, silently dropping digits.
  React.useEffect(() => {
    if (value.length === PIN_LENGTH) {
      onComplete(value);
      setValue('');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value]);

  const onKeyPress = (key: string) => {
    if (key === '') return;
    if (key === 'del') {
      setValue(v => v.slice(0, -1));
      return;
    }
    setValue(v => (v.length >= PIN_LENGTH ? v : v + key));
  };

  return (
    <View>
      <View style={styles.dots} testID="pin-dots">
        {Array.from({ length: PIN_LENGTH }).map((_, i) => (
          <View key={i} style={[styles.dot, i < value.length ? styles.dotFilled : null]} />
        ))}
      </View>

      {error ? <Text style={styles.error}>{error}</Text> : null}

      <View style={styles.keypad}>
        {KEYS.map((key, i) => (
          <PressableScale
            key={i}
            style={[styles.key, key === '' ? styles.keyHidden : null]}
            onPress={() => onKeyPress(key)}
            disabled={key === ''}
            testID={key === 'del' ? 'pin-key-delete' : key ? `pin-key-${key}` : undefined}
          >
            <Text style={styles.keyText}>{key === 'del' ? '⌫' : key}</Text>
          </PressableScale>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  dots: {
    flexDirection: 'row',
    justifyContent: 'center',
    marginBottom: spacing.lg,
  },
  dot: {
    width: 16,
    height: 16,
    borderRadius: radii.pill,
    borderWidth: 1.5,
    borderColor: colors.gold.base,
    marginHorizontal: spacing.sm,
  },
  dotFilled: { backgroundColor: colors.gold.base },
  error: {
    color: colors.status.error,
    fontSize: 13,
    textAlign: 'center',
    marginBottom: spacing.lg,
  },
  keypad: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'center',
    maxWidth: 300,
    alignSelf: 'center',
  },
  key: {
    width: 80,
    height: 64,
    alignItems: 'center',
    justifyContent: 'center',
  },
  keyHidden: { opacity: 0 },
  keyText: { fontSize: 24, fontWeight: '500', color: colors.textPrimary },
});

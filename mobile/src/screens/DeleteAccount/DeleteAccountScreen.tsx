import React from 'react';
import { View, ActivityIndicator, Alert, StyleSheet } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useDeletionRequest } from './useDeletionRequest';
import { PreSubmissionView } from './PreSubmissionView';
import { CoolOffView } from './CoolOffView';
import { colors } from '../../theme';
import type { RootStackParamList } from '../../navigation/RootNavigator';

type Nav = NativeStackNavigationProp<RootStackParamList, 'DeleteAccount'>;

export function DeleteAccountScreen() {
  const navigation = useNavigation<Nav>();
  const {
    screenState,
    activeRequest,
    blockers,
    isLoadingBlockers,
    submit,
    isSubmitting,
    submitError,
    cancel,
    isCancelling,
    cancelError,
  } = useDeletionRequest();

  const handleCancel = () => {
    cancel(undefined, {
      onSuccess: () => {
        // Alert.alert is this codebase's established confirmation-dialog
        // pattern (see EarlyExitScreen, CreateSusuScreen) — goBack() returns
        // to wherever this screen was entered from (Settings or Home).
        Alert.alert(
          'Deletion request cancelled',
          'Your account is no longer scheduled for deletion.',
          [{ text: 'OK', onPress: () => navigation.goBack() }],
        );
      },
    });
  };

  if (screenState === 'LOADING') {
    return (
      <View style={styles.centered}>
        <ActivityIndicator color={colors.gold.base} testID="delete-account-loading" />
      </View>
    );
  }

  if (screenState === 'COOL_OFF' && activeRequest) {
    return (
      <CoolOffView
        request={activeRequest}
        isCancelling={isCancelling}
        cancelError={cancelError}
        onCancel={handleCancel}
      />
    );
  }

  return (
    <PreSubmissionView
      blockers={blockers}
      isLoadingBlockers={isLoadingBlockers}
      isSubmitting={isSubmitting}
      submitError={submitError}
      onSubmit={() => submit()}
    />
  );
}

const styles = StyleSheet.create({
  centered: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.background },
});

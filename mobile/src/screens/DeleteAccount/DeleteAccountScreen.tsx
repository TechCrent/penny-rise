import React from 'react';
import { View, ActivityIndicator, Alert, StyleSheet } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useDeletionRequest } from './useDeletionRequest';
import { PreSubmissionView } from './PreSubmissionView';
import { CoolOffView } from './CoolOffView';
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
        // No dedicated account-settings screen exists yet to navigate back
        // to with a confirmation param — Alert.alert is this codebase's
        // established confirmation-dialog pattern (see EarlyExitScreen,
        // CreateSusuScreen), so it's used here instead of inventing a toast
        // dependency or a settings screen this issue doesn't require.
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
        <ActivityIndicator testID="delete-account-loading" />
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
  centered: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: '#F9FAFB' },
});

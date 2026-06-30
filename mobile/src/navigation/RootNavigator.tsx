import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { ActivityIndicator, StyleSheet, View } from 'react-native';
import { useAuth } from '../auth/AuthContext';

import AuthenticatedBootstrapScreen from '../screens/AuthenticatedBootstrapScreen';
import HomeScreen from '../screens/HomeScreen';
import RegisterScreen from '../screens/RegisterScreen';
import EmailVerificationPendingScreen from '../screens/EmailVerificationPendingScreen';
import LoginScreen from '../screens/LoginScreen';
import ForgotPasswordScreen from '../screens/ForgotPasswordScreen';
import ResetPasswordScreen from '../screens/ResetPasswordScreen';
import KycCardDetailsScreen from '../screens/KycCardDetailsScreen';
import KycDocumentUploadScreen from '../screens/KycDocumentUploadScreen';
import KycSubmissionPendingScreen from '../screens/KycSubmissionPendingScreen';
import {
  CreateVaultScreen,
  VaultDetailScreen,
  DepositScreen,
  WithdrawScreen,
  EarlyExitScreen,
  CancelEarlyExitScreen,
} from '../screens/Vault';
import {
  SusuListScreen,
  SusuDetailScreen,
  CreateSusuScreen,
  CreateSusuInviteScreen,
  JoinSusuScreen,
} from '../screens/susu';
import { RecipientPickerScreen } from '../screens/transfer/RecipientPickerScreen';
import { SendMoneyScreen } from '../screens/transfer/SendMoneyScreen';
import { TransferSuccessScreen } from '../screens/transfer/TransferSuccessScreen';
import type { RecipientResult, TransferResult } from '../api/transfers';

export type RootStackParamList = {
  Register: undefined;
  Login: { successBanner?: string } | undefined;
  ForgotPassword: undefined;
  ResetPassword: { token: string };
  EmailVerificationPending: { email?: string; token?: string };
  AuthenticatedBootstrap: undefined;
  Home: undefined;
  KycFlow: undefined;
  VaultList: undefined;
  VaultDetail: { vaultId: string; successMessage?: string };
  CreateVault: undefined;
  Transfer: undefined;
  Notifications: undefined;
  KycCardDetails: undefined;
  KycDocumentUpload: {
    submissionId: string;
    uploadUrls: {
      FRONT_OF_CARD: string;
      BACK_OF_CARD: string;
      SELFIE: string;
    };
  };
  KycSubmissionPending: { submissionId: string };
  Deposit: { vaultId: string };
  Withdraw: { vaultId: string };
  EarlyExit: { vaultId: string };
  CancelEarlyExit: { vaultId: string };
  SusuList: undefined;
  SusuDetail: { groupId: string };
  CreateSusu: undefined;
  CreateSusuInvite: {
    groupId: string;
    joinCode: string;
    groupName: string;
    contributionCedis: string;
    frequency: string;
    targetMemberCount: number;
  };
  JoinSusu: undefined;
  RecipientPicker: undefined;
  SendMoney: { recipient: RecipientResult };
  TransferSuccess: { result: TransferResult; recipientName: string };
};

const Stack = createNativeStackNavigator<RootStackParamList>();

const linking = {
  prefixes: ['stash://', 'https://stash.app'],
  config: {
    screens: {
      ResetPassword: { path: 'reset-password', parse: { token: String } },
      EmailVerificationPending: {
        path: 'verify-email',
        parse: { email: String, token: String },
      },
    },
  },
};

export { linking };

export default function RootNavigator() {
  const { isLoading, isAuthenticated } = useAuth();

  if (isLoading) {
    return (
      <View style={styles.loading}>
        <ActivityIndicator size="large" />
      </View>
    );
  }

  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      {isAuthenticated ? (
        <>
          <Stack.Screen name="AuthenticatedBootstrap" component={AuthenticatedBootstrapScreen} />
          <Stack.Screen name="Home" component={HomeScreen} />
          <Stack.Screen name="KycCardDetails" component={KycCardDetailsScreen} />
          <Stack.Screen name="KycDocumentUpload" component={KycDocumentUploadScreen} />
          <Stack.Screen name="KycSubmissionPending" component={KycSubmissionPendingScreen} />
          <Stack.Screen name="CreateVault" component={CreateVaultScreen} />
          <Stack.Screen name="VaultDetail" component={VaultDetailScreen} />
          <Stack.Screen name="Deposit" component={DepositScreen} />
          <Stack.Screen name="Withdraw" component={WithdrawScreen} />
          <Stack.Screen name="EarlyExit" component={EarlyExitScreen} />
          <Stack.Screen name="CancelEarlyExit" component={CancelEarlyExitScreen} />
          <Stack.Screen name="SusuList" component={SusuListScreen} />
          <Stack.Screen name="SusuDetail" component={SusuDetailScreen} />
          <Stack.Screen name="CreateSusu" component={CreateSusuScreen} />
          <Stack.Screen name="CreateSusuInvite" component={CreateSusuInviteScreen} />
          <Stack.Screen name="JoinSusu" component={JoinSusuScreen} />
          <Stack.Screen name="RecipientPicker" component={RecipientPickerScreen} />
          <Stack.Screen name="SendMoney" component={SendMoneyScreen} />
          <Stack.Screen name="TransferSuccess" component={TransferSuccessScreen} />
        </>
      ) : (
        <>
          <Stack.Screen name="Login" component={LoginScreen} />
          <Stack.Screen name="Register" component={RegisterScreen} />
          <Stack.Screen name="ForgotPassword" component={ForgotPasswordScreen} />
          <Stack.Screen name="ResetPassword" component={ResetPasswordScreen} />
          <Stack.Screen
            name="EmailVerificationPending"
            component={EmailVerificationPendingScreen}
          />
        </>
      )}
    </Stack.Navigator>
  );
}

const styles = StyleSheet.create({
  loading: { flex: 1, justifyContent: 'center', alignItems: 'center' },
});

import React, { useEffect, useState } from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import type { NavigatorScreenParams } from '@react-navigation/native';
import { ActivityIndicator, StyleSheet, View } from 'react-native';
import { useAuth } from '../auth/AuthContext';
import { hasSeenOnboarding } from '../storage/onboardingStorage';

import AuthenticatedBootstrapScreen from '../screens/AuthenticatedBootstrapScreen';
import { AppLockSetupPromptScreen } from '../screens/AppLockSetupPromptScreen';
import type { PostAuthRoute } from './resolvePostAuthNavigation';
import MainTabNavigator, { type MainTabParamList } from './MainTabNavigator';
import OnboardingScreen from '../screens/Onboarding/OnboardingScreen';
import WelcomeScreen from '../screens/WelcomeScreen';
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
  VaultListScreen,
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
  SusuModernComingSoonScreen,
} from '../screens/susu';
import { RecipientPickerScreen } from '../screens/transfer/RecipientPickerScreen';
import { SendMoneyScreen } from '../screens/transfer/SendMoneyScreen';
import { TransferSuccessScreen } from '../screens/transfer/TransferSuccessScreen';
import { WalletScreen } from '../screens/wallet/WalletScreen';
import { WalletWithdrawComingSoonScreen } from '../screens/wallet/WalletWithdrawComingSoonScreen';
import { NotificationInboxScreen } from '../screens/NotificationInbox/NotificationInboxScreen';
import { TransactionHistoryScreen } from '../screens/TransactionHistory/TransactionHistoryScreen';
import { ChallengesListScreen } from '../screens/Challenges/ChallengesListScreen';
import { ChallengeDetailScreen } from '../screens/Challenges/ChallengeDetailScreen';
import { DeleteAccountScreen } from '../screens/DeleteAccount/DeleteAccountScreen';
import { UpgradeScreen } from '../screens/Subscription/UpgradeScreen';
import { DowngradeScreen } from '../screens/Subscription/DowngradeScreen';
import { SettingsScreen } from '../screens/Settings/SettingsScreen';
import { ProfileScreen } from '../screens/Settings/ProfileScreen';
import { ChangePasswordScreen } from '../screens/Settings/ChangePasswordScreen';
import { AppLockSettingsScreen } from '../screens/Settings/AppLockSettingsScreen';
import { LegalScreen } from '../screens/Settings/LegalScreen';
import { AccountPickerScreen } from '../screens/AccountPicker/AccountPickerScreen';
import type { RecipientResult, TransferResult } from '../api/transfers';

export type RootStackParamList = {
  Onboarding: undefined;
  Welcome: undefined;
  Register: undefined;
  Login: { successBanner?: string } | undefined;
  ForgotPassword: undefined;
  ResetPassword: { token: string };
  EmailVerificationPending: { email?: string; token?: string };
  AuthenticatedBootstrap: undefined;
  AppLockSetupPrompt: { nextRoute: PostAuthRoute };
  Main: NavigatorScreenParams<MainTabParamList> | undefined;
  VaultList: undefined;
  VaultDetail: { vaultId: string; successMessage?: string };
  CreateVault: undefined;
  Wallet: undefined;
  WalletWithdrawComingSoon: undefined;
  Notifications: undefined;
  TransactionHistory: { scope?: 'vault' | 'wallet' } | undefined;
  ChallengesList: undefined;
  ChallengeDetail: { challengeId: string };
  DeleteAccount: undefined;
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
  AccountPicker: { mode: 'DEPOSIT' | 'WITHDRAW' };
  Deposit: { vaultId?: string };
  Withdraw: { vaultId: string };
  EarlyExit: { vaultId: string };
  CancelEarlyExit: { vaultId: string };
  SusuList: undefined;
  SusuDetail: { groupId: string };
  CreateSusu: undefined;
  SusuModernComingSoon: undefined;
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
  SubscriptionUpgrade: undefined;
  SubscriptionDowngrade: undefined;
  Settings: undefined;
  EditProfile: undefined;
  ChangePassword: undefined;
  AppLockSettings: undefined;
  Legal: undefined;
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
      SubscriptionUpgrade: { path: 'subscription/upgrade' },
    },
  },
};

export { linking };

export default function RootNavigator() {
  const { isLoading, isAuthenticated } = useAuth();
  const [onboardingSeen, setOnboardingSeen] = useState<boolean | null>(null);

  useEffect(() => {
    hasSeenOnboarding().then(setOnboardingSeen);
  }, []);

  if (isLoading || onboardingSeen === null) {
    return (
      <View style={styles.loading}>
        <ActivityIndicator size="large" />
      </View>
    );
  }

  return (
    <Stack.Navigator
      screenOptions={{ headerShown: false }}
      initialRouteName={isAuthenticated ? undefined : onboardingSeen ? 'Welcome' : 'Onboarding'}
    >
      {isAuthenticated ? (
        <>
          <Stack.Screen name="AuthenticatedBootstrap" component={AuthenticatedBootstrapScreen} />
          <Stack.Screen name="AppLockSetupPrompt" component={AppLockSetupPromptScreen} />
          <Stack.Screen name="Main" component={MainTabNavigator} />
          <Stack.Screen name="KycCardDetails" component={KycCardDetailsScreen} />
          <Stack.Screen name="KycDocumentUpload" component={KycDocumentUploadScreen} />
          <Stack.Screen name="KycSubmissionPending" component={KycSubmissionPendingScreen} />
          <Stack.Screen name="CreateVault" component={CreateVaultScreen} />
          <Stack.Screen name="VaultList" component={VaultListScreen} />
          <Stack.Screen name="VaultDetail" component={VaultDetailScreen} />
          <Stack.Screen name="AccountPicker" component={AccountPickerScreen} />
          <Stack.Screen name="Deposit" component={DepositScreen} />
          <Stack.Screen name="Withdraw" component={WithdrawScreen} />
          <Stack.Screen name="EarlyExit" component={EarlyExitScreen} />
          <Stack.Screen name="CancelEarlyExit" component={CancelEarlyExitScreen} />
          <Stack.Screen name="SusuList" component={SusuListScreen} />
          <Stack.Screen name="SusuDetail" component={SusuDetailScreen} />
          <Stack.Screen name="CreateSusu" component={CreateSusuScreen} />
          <Stack.Screen name="SusuModernComingSoon" component={SusuModernComingSoonScreen} />
          <Stack.Screen name="CreateSusuInvite" component={CreateSusuInviteScreen} />
          <Stack.Screen name="JoinSusu" component={JoinSusuScreen} />
          <Stack.Screen name="RecipientPicker" component={RecipientPickerScreen} />
          <Stack.Screen name="SendMoney" component={SendMoneyScreen} />
          <Stack.Screen name="TransferSuccess" component={TransferSuccessScreen} />
          <Stack.Screen name="Wallet" component={WalletScreen} />
          <Stack.Screen
            name="WalletWithdrawComingSoon"
            component={WalletWithdrawComingSoonScreen}
          />
          <Stack.Screen name="Notifications" component={NotificationInboxScreen} />
          <Stack.Screen name="TransactionHistory" component={TransactionHistoryScreen} />
          <Stack.Screen name="ChallengesList" component={ChallengesListScreen} />
          <Stack.Screen name="ChallengeDetail" component={ChallengeDetailScreen} />
          <Stack.Screen name="DeleteAccount" component={DeleteAccountScreen} />
          <Stack.Screen name="SubscriptionUpgrade" component={UpgradeScreen} />
          <Stack.Screen name="SubscriptionDowngrade" component={DowngradeScreen} />
          <Stack.Screen name="Settings" component={SettingsScreen} />
          <Stack.Screen name="EditProfile" component={ProfileScreen} />
          <Stack.Screen name="ChangePassword" component={ChangePasswordScreen} />
          <Stack.Screen name="AppLockSettings" component={AppLockSettingsScreen} />
          <Stack.Screen name="Legal" component={LegalScreen} />
        </>
      ) : (
        <>
          <Stack.Screen name="Onboarding" component={OnboardingScreen} />
          <Stack.Screen name="Welcome" component={WelcomeScreen} />
          <Stack.Screen name="Login" component={LoginScreen} />
          <Stack.Screen name="Register" component={RegisterScreen} />
          <Stack.Screen name="ForgotPassword" component={ForgotPasswordScreen} />
          <Stack.Screen name="ResetPassword" component={ResetPasswordScreen} />
          <Stack.Screen
            name="EmailVerificationPending"
            component={EmailVerificationPendingScreen}
          />
          <Stack.Screen name="Legal" component={LegalScreen} />
        </>
      )}
    </Stack.Navigator>
  );
}

const styles = StyleSheet.create({
  loading: { flex: 1, justifyContent: 'center', alignItems: 'center' },
});

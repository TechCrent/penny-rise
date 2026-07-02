import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../../navigation/RootNavigator';

/**
 * Maps a notification's type + data to a navigation action. Deliberately
 * driven by `type`/`data` (both already in the API response) rather than
 * the backend's `deep_link` column — v0.5-015 dropped `deep_link` from the
 * response DTO, and typed navigation params beat parsing a `stash://...`
 * URL string anyway.
 *
 * Only types with a real destination screen in RootStackParamList are
 * mapped. KYC decisions and challenge completions fall through to
 * noNavigation because this app doesn't have a KYC status screen or a
 * challenges list yet (challenges ships in v0.5-022) — the tap still marks
 * the notification read, it just doesn't navigate anywhere.
 */
type NotificationNavigation = Pick<NativeStackNavigationProp<RootStackParamList>, 'navigate'>;

export function navigateForNotification(
  navigation: NotificationNavigation,
  type: string,
  data: Record<string, unknown>,
): void {
  switch (type) {
    case 'DEPOSIT_SUCCESS':
    case 'WITHDRAWAL_SUCCESS': {
      const vaultId = typeof data.vault_id === 'string' ? data.vault_id : undefined;
      if (vaultId) {
        navigation.navigate('VaultDetail', { vaultId });
      } else {
        navigation.navigate('Wallet'); // no vault_id → this was a wallet-level operation
      }
      return;
    }

    case 'TRANSFER_RECEIVED':
      navigation.navigate('Wallet');
      return;

    default:
      // KYC_APPROVED, KYC_REJECTED, ChallengeCompletedEvent, ACCOUNT_SUSPENDED,
      // and anything else not yet mapped: no navigation. Extend this switch
      // as the destination screens land.
      return;
  }
}

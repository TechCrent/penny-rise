import { navigateForNotification } from '../../../src/features/notifications/deepLinkRouter';

describe('navigateForNotification', () => {
  function mockNavigation() {
    return { navigate: jest.fn() } as any;
  }

  it('DEPOSIT_SUCCESS with a vault_id navigates to VaultDetail', () => {
    const nav = mockNavigation();
    navigateForNotification(nav, 'DEPOSIT_SUCCESS', { vault_id: 'vault-1' });
    expect(nav.navigate).toHaveBeenCalledWith('VaultDetail', { vaultId: 'vault-1' });
  });

  it('DEPOSIT_SUCCESS with no vault_id navigates to Wallet', () => {
    const nav = mockNavigation();
    navigateForNotification(nav, 'DEPOSIT_SUCCESS', {});
    expect(nav.navigate).toHaveBeenCalledWith('Wallet');
  });

  it('WITHDRAWAL_SUCCESS with a vault_id navigates to VaultDetail', () => {
    const nav = mockNavigation();
    navigateForNotification(nav, 'WITHDRAWAL_SUCCESS', { vault_id: 'vault-2' });
    expect(nav.navigate).toHaveBeenCalledWith('VaultDetail', { vaultId: 'vault-2' });
  });

  it('TRANSFER_RECEIVED navigates to Wallet', () => {
    const nav = mockNavigation();
    navigateForNotification(nav, 'TRANSFER_RECEIVED', {});
    expect(nav.navigate).toHaveBeenCalledWith('Wallet');
  });

  it('an unmapped type does not navigate anywhere', () => {
    const nav = mockNavigation();
    navigateForNotification(nav, 'ACCOUNT_SUSPENDED', {});
    expect(nav.navigate).not.toHaveBeenCalled();
  });

  it('KYC_APPROVED does not navigate anywhere (no KYC status screen exists yet)', () => {
    const nav = mockNavigation();
    navigateForNotification(nav, 'KYC_APPROVED', {});
    expect(nav.navigate).not.toHaveBeenCalled();
  });
});

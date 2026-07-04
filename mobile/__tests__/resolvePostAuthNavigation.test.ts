import { resolvePostAuthNavigation } from '../src/navigation/resolvePostAuthNavigation';
import * as kycApi from '../src/api/kyc';
import * as kycStorage from '../src/storage/kycStorage';
import * as authSession from '../src/auth/authSession';
import * as jwt from '../src/auth/jwt';

jest.mock('../src/api/kyc');
jest.mock('../src/storage/kycStorage');
jest.mock('../src/auth/authSession');
jest.mock('../src/auth/jwt');

describe('resolvePostAuthNavigation', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.spyOn(authSession, 'getAccessToken').mockResolvedValue(null);
    jest.spyOn(jwt, 'decodeJwtPayload').mockReturnValue({ exp: Math.floor(Date.now() / 1000) + 3600 });
    jest.spyOn(jwt, 'decodeUserIdFromJwt').mockReturnValue(null);
    jest.spyOn(kycStorage, 'loadKycSubmission').mockResolvedValue(null);
    jest.spyOn(kycStorage, 'clearKycSubmission').mockResolvedValue(undefined);
    jest.spyOn(kycApi, 'getMySubmission').mockResolvedValue(null);
  });

  it('routes APPROVED users to Home', async () => {
    await expect(resolvePostAuthNavigation('APPROVED')).resolves.toEqual({ name: 'Home' });
  });

  it('routes PENDING users to KycCardDetails when no in-progress submission', async () => {
    await expect(resolvePostAuthNavigation('PENDING')).resolves.toEqual({ name: 'KycCardDetails' });
  });

  it('routes to document upload when local submission is stored', async () => {
    jest.spyOn(authSession, 'getAccessToken').mockResolvedValue('access-token');
    jest.spyOn(jwt, 'decodeUserIdFromJwt').mockReturnValue('user-1');
    jest.spyOn(kycApi, 'getMySubmission').mockResolvedValue({
      id: 'sub-1',
      status: 'PENDING_DOCUMENTS',
      submitted_at: null,
      updated_at: '2026-01-01T00:00:00Z',
      rejection_reason: null,
    });
    jest.spyOn(kycStorage, 'loadKycSubmission').mockResolvedValue({
      submissionId: 'sub-1',
      ownerUserId: 'user-1',
      uploadUrls: {
        FRONT_OF_CARD: 'https://example.com/front',
        BACK_OF_CARD: 'https://example.com/back',
        SELFIE: 'https://example.com/selfie',
      },
      uploadedTypes: ['FRONT_OF_CARD'],
    });

    await expect(resolvePostAuthNavigation('PENDING')).resolves.toEqual({
      name: 'KycDocumentUpload',
      params: {
        submissionId: 'sub-1',
        uploadUrls: {
          FRONT_OF_CARD: 'https://example.com/front',
          BACK_OF_CARD: 'https://example.com/back',
          SELFIE: 'https://example.com/selfie',
        },
      },
    });
  });

  it('clears stale local submission when it does not match the active server submission', async () => {
    jest.spyOn(authSession, 'getAccessToken').mockResolvedValue('access-token');
    jest.spyOn(jwt, 'decodeUserIdFromJwt').mockReturnValue('user-1');
    jest.spyOn(kycApi, 'getMySubmission').mockResolvedValue(null);
    jest.spyOn(kycStorage, 'loadKycSubmission').mockResolvedValue({
      submissionId: 'sub-1',
      ownerUserId: 'user-1',
      uploadUrls: {
        FRONT_OF_CARD: 'https://example.com/front',
        BACK_OF_CARD: 'https://example.com/back',
        SELFIE: 'https://example.com/selfie',
      },
      uploadedTypes: ['FRONT_OF_CARD'],
    });

    await expect(resolvePostAuthNavigation('PENDING')).resolves.toEqual({ name: 'KycCardDetails' });
    expect(kycStorage.clearKycSubmission).toHaveBeenCalled();
  });

  it('routes SUBMITTED users to pending screen when submission exists', async () => {
    jest.spyOn(kycApi, 'getMySubmission').mockResolvedValue({
      id: 'sub-2',
      status: 'REVIEWING',
      submitted_at: '2026-01-01T00:00:00Z',
      updated_at: '2026-01-01T00:00:00Z',
      rejection_reason: null,
    });

    await expect(resolvePostAuthNavigation('SUBMITTED')).resolves.toEqual({
      name: 'KycSubmissionPending',
      params: { submissionId: 'sub-2' },
    });
  });
});

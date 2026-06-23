import { resolvePostAuthNavigation } from '../src/navigation/resolvePostAuthNavigation';
import * as kycApi from '../src/api/kyc';
import * as kycStorage from '../src/storage/kycStorage';

jest.mock('../src/api/kyc');
jest.mock('../src/storage/kycStorage');

describe('resolvePostAuthNavigation', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.spyOn(kycStorage, 'loadKycSubmission').mockResolvedValue(null);
    jest.spyOn(kycApi, 'getMySubmission').mockResolvedValue(null);
  });

  it('routes APPROVED users to Home', async () => {
    await expect(resolvePostAuthNavigation('APPROVED')).resolves.toEqual({ name: 'Home' });
  });

  it('routes PENDING users to KycCardDetails when no in-progress submission', async () => {
    await expect(resolvePostAuthNavigation('PENDING')).resolves.toEqual({ name: 'KycCardDetails' });
  });

  it('routes to document upload when local submission is stored', async () => {
    jest.spyOn(kycStorage, 'loadKycSubmission').mockResolvedValue({
      submissionId: 'sub-1',
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

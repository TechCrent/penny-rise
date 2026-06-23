import { decodeJwtPayload, decodeKycStatusFromJwt } from '../src/auth/jwt';

function makeJwt(payload: Record<string, unknown>): string {
  const header = globalThis.btoa(JSON.stringify({ alg: 'none', typ: 'JWT' }));
  const body = globalThis.btoa(JSON.stringify(payload));
  return `${header}.${body}.signature`;
}

describe('jwt', () => {
  it('decodeJwtPayload reads payload claims', () => {
    const token = makeJwt({ kyc_status: 'APPROVED', sub: 'user-1' });
    expect(decodeJwtPayload(token)).toMatchObject({ kyc_status: 'APPROVED', sub: 'user-1' });
  });

  it('decodeKycStatusFromJwt returns kyc_status claim', () => {
    const token = makeJwt({ kyc_status: 'PENDING' });
    expect(decodeKycStatusFromJwt(token)).toBe('PENDING');
  });

  it('decodeKycStatusFromJwt defaults to PENDING when claim missing', () => {
    const token = makeJwt({ sub: 'user-1' });
    expect(decodeKycStatusFromJwt(token)).toBe('PENDING');
  });
});

function base64UrlDecode(input: string): string {
  const base64 = input.replace(/-/g, '+').replace(/_/g, '/');
  const padLength = (4 - (base64.length % 4)) % 4;
  const padded = base64 + '='.repeat(padLength);

  if (typeof globalThis.atob === 'function') {
    return globalThis.atob(padded);
  }

  throw new Error('Base64 decoding is unavailable in this environment');
}

export function decodeJwtPayload(token: string): Record<string, unknown> {
  const parts = token.split('.');
  if (parts.length !== 3) {
    throw new Error('Invalid JWT format');
  }

  return JSON.parse(base64UrlDecode(parts[1])) as Record<string, unknown>;
}

export function decodeKycStatusFromJwt(accessToken: string): string {
  const payload = decodeJwtPayload(accessToken);
  const kycStatus = payload.kyc_status;
  return typeof kycStatus === 'string' ? kycStatus : 'PENDING';
}

export function decodeUserIdFromJwt(accessToken: string): string | null {
  const payload = decodeJwtPayload(accessToken);
  const subject = payload.sub;
  return typeof subject === 'string' && subject.length > 0 ? subject : null;
}

export function isAccessTokenExpired(accessToken: string, nowMs = Date.now()): boolean {
  try {
    const payload = decodeJwtPayload(accessToken);
    const exp = payload.exp;
    return typeof exp === 'number' && nowMs / 1000 >= exp;
  } catch {
    return true;
  }
}

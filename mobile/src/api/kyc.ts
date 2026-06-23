import { apiClient } from './client';

export interface CreateSubmissionRequest {
  ghana_card_number: string;
  full_name: string;
}

export interface CreateSubmissionResponse {
  id: string;
  status: string;
  upload_urls: {
    FRONT_OF_CARD: string;
    BACK_OF_CARD: string;
    SELFIE: string;
  };
}

export interface SubmissionStatusResponse {
  id: string;
  status: string;
  submitted_at: string;
  updated_at: string;
  rejection_reason: string | null;
}

export interface ConfirmDocumentRequest {
  provider_event_id: string;
  document_type: 'FRONT_OF_CARD' | 'BACK_OF_CARD' | 'SELFIE';
  storage_key: string;
  content_type: string;
  size_bytes: number;
  sha256_hash: string;
}

export async function createSubmission(
  request: CreateSubmissionRequest,
): Promise<CreateSubmissionResponse> {
  const { data } = await apiClient.post<CreateSubmissionResponse>(
    '/api/v1/kyc/submissions',
    request,
  );
  return data;
}

export async function getSubmissionStatus(submissionId: string): Promise<SubmissionStatusResponse> {
  const { data } = await apiClient.get<SubmissionStatusResponse>(
    `/api/v1/kyc/submissions/${submissionId}`,
  );
  return data;
}

export async function getMySubmission(): Promise<SubmissionStatusResponse | null> {
  try {
    const { data } = await apiClient.get<SubmissionStatusResponse>('/api/v1/kyc/submissions/me');
    return data;
  } catch {
    return null;
  }
}

export async function uploadDocumentToSignedUrl(
  signedUrl: string,
  fileUri: string,
  contentType: string,
  onProgress?: (progress: number) => void,
): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();

    xhr.upload.addEventListener('progress', event => {
      if (event.lengthComputable && onProgress) {
        onProgress(event.loaded / event.total);
      }
    });

    xhr.addEventListener('load', () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve();
      } else {
        reject(new Error(`Upload failed with status ${xhr.status}`));
      }
    });

    xhr.addEventListener('error', () => reject(new Error('Network error during upload')));

    xhr.open('PUT', signedUrl);
    xhr.setRequestHeader('Content-Type', contentType);
    xhr.send({ uri: fileUri, type: contentType, name: 'document' } as unknown as Document);
  });
}

export async function confirmDocumentUpload(
  submissionId: string,
  request: ConfirmDocumentRequest,
): Promise<void> {
  await apiClient.post(`/api/v1/kyc/submissions/${submissionId}/documents`, request, {
    headers: {
      'X-Storage-Signature':
        process.env.EXPO_PUBLIC_KYC_WEBHOOK_SIGNATURE ?? 'local-dev-signature-do-not-use-in-prod',
    },
  });
}

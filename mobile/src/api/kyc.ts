import axios from 'axios';
import * as FileSystem from 'expo-file-system/legacy';

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
  } catch (err) {
    if (axios.isAxiosError(err) && err.response?.status === 404) {
      return null;
    }
    console.error(err);
    return null;
  }
}

function normalizeSignedUrlForDeviceReachability(signedUrl: string): string {
  const apiBaseUrl = apiClient.defaults.baseURL;
  if (!apiBaseUrl) {
    return signedUrl;
  }

  try {
    const apiUrl = new URL(apiBaseUrl);
    const localStorageMatch = signedUrl.match(
      /^https?:\/\/[^/]+(\/internal\/local-storage\/upload\/[^?]+(?:\?.*)?)$/i,
    );
    if (localStorageMatch) {
      return `${apiUrl.origin}${localStorageMatch[1]}`;
    }

    const uploadUrl = new URL(signedUrl);
    const isLoopbackHost = uploadUrl.hostname === 'localhost' || uploadUrl.hostname === '127.0.0.1';
    if (!isLoopbackHost) {
      return signedUrl;
    }

    uploadUrl.hostname = apiUrl.hostname;
    uploadUrl.protocol = apiUrl.protocol;
    uploadUrl.port = apiUrl.port;
    return uploadUrl.toString();
  } catch (err) {
    console.error(err);
    return signedUrl;
  }
}

export async function uploadDocumentToSignedUrl(
  signedUrl: string,
  fileUri: string,
  contentType: string,
  onProgress?: (progress: number) => void,
): Promise<void> {
  const reachableUrl = normalizeSignedUrlForDeviceReachability(signedUrl);

  const result = await FileSystem.uploadAsync(reachableUrl, fileUri, {
    httpMethod: 'PUT',
    uploadType: FileSystem.FileSystemUploadType.BINARY_CONTENT,
    headers: {
      'Content-Type': contentType,
    },
  });

  if (result.status >= 200 && result.status < 300) {
    return;
  }

  if (onProgress) {
    onProgress(1);
  }

  throw new Error(`Upload failed with status ${result.status}`);
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

import * as FileSystem from 'expo-file-system/legacy';
import { uploadDocumentToSignedUrl } from '../src/api/kyc';

jest.mock('expo-file-system/legacy', () => ({
  FileSystemUploadType: { BINARY_CONTENT: 'BINARY_CONTENT' },
  createUploadTask: jest.fn(),
}));

describe('uploadDocumentToSignedUrl', () => {
  beforeEach(() => jest.clearAllMocks());

  it('reports real intermediate progress via the createUploadTask callback, not just 0 then 1', async () => {
    let capturedCallback:
      | ((data: { totalBytesSent: number; totalBytesExpectedToSend: number }) => void)
      | undefined;
    (FileSystem.createUploadTask as jest.Mock).mockImplementation(
      (_url, _fileUri, _options, callback) => {
        capturedCallback = callback;
        return {
          uploadAsync: jest.fn().mockImplementation(async () => {
            capturedCallback?.({ totalBytesSent: 50, totalBytesExpectedToSend: 200 });
            capturedCallback?.({ totalBytesSent: 200, totalBytesExpectedToSend: 200 });
            return { status: 201, body: '', headers: {} };
          }),
        };
      },
    );

    const progressUpdates: number[] = [];
    await uploadDocumentToSignedUrl(
      'http://localhost:8080/internal/local-storage/upload/x.jpg',
      'file:///tmp/photo.jpg',
      'image/jpeg',
      p => progressUpdates.push(p),
    );

    expect(progressUpdates).toEqual([0.25, 1, 1]);
  });

  it('throws on a non-2xx status and does not report false 100% progress on failure', async () => {
    (FileSystem.createUploadTask as jest.Mock).mockImplementation(() => ({
      uploadAsync: jest.fn().mockResolvedValue({ status: 500, body: '', headers: {} }),
    }));

    const progressUpdates: number[] = [];
    await expect(
      uploadDocumentToSignedUrl(
        'http://localhost:8080/internal/local-storage/upload/x.jpg',
        'file:///tmp/photo.jpg',
        'image/jpeg',
        p => progressUpdates.push(p),
      ),
    ).rejects.toThrow('Upload failed with status 500');

    expect(progressUpdates).not.toContain(1);
  });
});

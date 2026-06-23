import React from 'react';
import {
  View,
  Text,
  Image,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
  Alert,
} from 'react-native';
import * as ImagePicker from 'expo-image-picker';

export type UploadState = 'idle' | 'uploading' | 'success' | 'error';

interface DocumentUploadSlotProps {
  label: string;
  description: string;
  state: UploadState;
  progress: number;
  onImageSelected: (uri: string) => void;
  previewUri?: string;
  error?: string;
}

export function DocumentUploadSlot({
  label,
  description,
  state,
  progress,
  onImageSelected,
  previewUri,
  error,
}: DocumentUploadSlotProps) {
  const pickImage = async () => {
    const { status } = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (status !== 'granted') {
      Alert.alert('Permission required', 'Camera roll permission is required to upload documents.');
      return;
    }
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      allowsEditing: true,
      quality: 0.85,
    });
    if (!result.canceled && result.assets[0]) {
      onImageSelected(result.assets[0].uri);
    }
  };

  const takePhoto = async () => {
    const { status } = await ImagePicker.requestCameraPermissionsAsync();
    if (status !== 'granted') {
      Alert.alert('Permission required', 'Camera permission is required to capture documents.');
      return;
    }
    const result = await ImagePicker.launchCameraAsync({
      allowsEditing: true,
      quality: 0.85,
    });
    if (!result.canceled && result.assets[0]) {
      onImageSelected(result.assets[0].uri);
    }
  };

  const progressPercent = Math.round(progress * 100);
  const isUploading = state === 'uploading';

  return (
    <View style={styles.container}>
      <Text style={styles.label}>{label}</Text>
      <Text style={styles.description}>{description}</Text>

      {state === 'success' && previewUri ? (
        <View style={styles.successSlot}>
          <Image source={{ uri: previewUri }} style={styles.preview} />
          <Text style={styles.successBadge}>Uploaded</Text>
          <TouchableOpacity onPress={pickImage} style={styles.retakeButton}>
            <Text style={styles.retakeText}>Retake</Text>
          </TouchableOpacity>
        </View>
      ) : (
        <View style={[styles.uploadSlot, error ? styles.uploadSlotError : null]}>
          {isUploading ? (
            <View style={styles.uploadingContainer}>
              <ActivityIndicator size="small" color="#1A1A1A" />
              <Text style={styles.uploadingText}>Uploading {progressPercent}%</Text>
              <View style={styles.progressBar}>
                <View
                  style={[
                    styles.progressFill,
                    { transform: [{ scaleX: Math.max(progress, 0.01) }] },
                  ]}
                />
              </View>
            </View>
          ) : (
            <View style={styles.buttonRow}>
              <TouchableOpacity style={styles.captureButton} onPress={takePhoto}>
                <Text style={styles.captureButtonText}>Camera</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.captureButton} onPress={pickImage}>
                <Text style={styles.captureButtonText}>Gallery</Text>
              </TouchableOpacity>
            </View>
          )}
        </View>
      )}

      {state === 'error' ? (
        <Text style={styles.errorText}>{error ?? 'Upload failed. Tap to retry.'}</Text>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { marginBottom: 24 },
  label: { fontSize: 15, fontWeight: '600', color: '#111827', marginBottom: 4 },
  description: { fontSize: 13, color: '#6B7280', marginBottom: 10 },
  uploadSlot: {
    borderWidth: 1.5,
    borderColor: '#D1D5DB',
    borderStyle: 'dashed',
    borderRadius: 10,
    padding: 16,
    minHeight: 100,
    justifyContent: 'center',
  },
  uploadSlotError: { borderColor: '#EF4444' },
  buttonRow: { flexDirection: 'row', gap: 12, justifyContent: 'center' },
  captureButton: {
    backgroundColor: '#F3F4F6',
    borderRadius: 8,
    paddingVertical: 10,
    paddingHorizontal: 18,
  },
  captureButtonText: { fontSize: 14, color: '#111827', fontWeight: '500' },
  uploadingContainer: { alignItems: 'center', gap: 8 },
  uploadingText: { fontSize: 13, color: '#6B7280' },
  progressBar: {
    width: '80%',
    height: 4,
    backgroundColor: '#E5E7EB',
    borderRadius: 2,
    overflow: 'hidden',
  },
  progressFill: {
    width: '100%',
    height: 4,
    backgroundColor: '#1A1A1A',
    borderRadius: 2,
    transformOrigin: 'left',
  },
  successSlot: { alignItems: 'center', gap: 10 },
  preview: { width: '100%', height: 150, borderRadius: 10, resizeMode: 'cover' },
  successBadge: { fontSize: 13, color: '#059669', fontWeight: '600' },
  retakeButton: { padding: 6 },
  retakeText: { fontSize: 13, color: '#6B7280', textDecorationLine: 'underline' },
  errorText: { fontSize: 12, color: '#EF4444', marginTop: 4 },
});

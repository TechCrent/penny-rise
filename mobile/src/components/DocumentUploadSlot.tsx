import React, { useEffect } from 'react';
import { View, Text, Image, StyleSheet, ActivityIndicator, Alert } from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import Animated, {
  Easing,
  useAnimatedStyle,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { Icon, PressableScale } from './ui';
import { colors, radii, spacing, typography } from '../theme';

export type UploadState = 'idle' | 'uploading' | 'success' | 'error';

export interface SelectedImage {
  uri: string;
  mimeType?: string;
  fileName?: string;
}

interface DocumentUploadSlotProps {
  label: string;
  description: string;
  state: UploadState;
  progress: number;
  onImageSelected: (image: SelectedImage) => void;
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
      onImageSelected({
        uri: result.assets[0].uri,
        mimeType: result.assets[0].mimeType ?? undefined,
        fileName: result.assets[0].fileName ?? undefined,
      });
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
      onImageSelected({
        uri: result.assets[0].uri,
        mimeType: result.assets[0].mimeType ?? undefined,
        fileName: result.assets[0].fileName ?? undefined,
      });
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
          <Image source={{ uri: previewUri }} style={styles.preview} resizeMode="cover" />
          <View style={styles.successBadgeRow}>
            <Icon name="checkmark-circle" size={15} color={colors.status.success} />
            <Text style={styles.successBadge}>Uploaded</Text>
          </View>
          <PressableScale onPress={pickImage} style={styles.retakeButton}>
            <Text style={styles.retakeText}>Retake</Text>
          </PressableScale>
        </View>
      ) : (
        <View style={[styles.uploadSlot, error ? styles.uploadSlotError : null]}>
          {isUploading ? (
            <View style={styles.uploadingContainer}>
              <ActivityIndicator size="small" color={colors.gold.base} />
              <Text style={styles.uploadingText}>Uploading {progressPercent}%</Text>
              <UploadProgressBar progress={progress} />
            </View>
          ) : (
            <View style={styles.buttonRow}>
              <PressableScale style={styles.captureButton} onPress={takePhoto}>
                <Icon name="camera-outline" size={16} color={colors.textPrimary} />
                <Text style={styles.captureButtonText}>Camera</Text>
              </PressableScale>
              <PressableScale style={styles.captureButton} onPress={pickImage}>
                <Icon name="images-outline" size={16} color={colors.textPrimary} />
                <Text style={styles.captureButtonText}>Gallery</Text>
              </PressableScale>
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

function UploadProgressBar({ progress }: { progress: number }) {
  const width = useSharedValue(0);

  useEffect(() => {
    width.value = withTiming(Math.max(progress, 0.01) * 100, {
      duration: 200,
      easing: Easing.out(Easing.cubic),
    });
  }, [progress, width]);

  const animatedStyle = useAnimatedStyle(() => ({ width: `${width.value}%` }));

  return (
    <View style={styles.progressBar}>
      <Animated.View style={[styles.progressFill, animatedStyle]} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { marginBottom: spacing['2xl'] },
  label: { ...typography.bodyMedium, color: colors.textPrimary, marginBottom: spacing.xxs },
  description: { fontSize: 13, color: colors.textSecondary, marginBottom: spacing.sm },
  uploadSlot: {
    borderWidth: 1.5,
    borderColor: colors.borderStrong,
    borderStyle: 'dashed',
    borderRadius: radii.md,
    padding: spacing.lg,
    minHeight: 100,
    justifyContent: 'center',
  },
  uploadSlotError: { borderColor: colors.status.error },
  buttonRow: { flexDirection: 'row', gap: spacing.md, justifyContent: 'center' },
  captureButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.xs,
    backgroundColor: colors.neutral[100],
    borderRadius: radii.sm,
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.lg,
  },
  captureButtonText: { fontSize: 14, color: colors.textPrimary, fontWeight: '500' },
  uploadingContainer: { alignItems: 'center', gap: spacing.sm },
  uploadingText: { fontSize: 13, color: colors.textSecondary },
  progressBar: {
    width: '80%',
    height: 4,
    backgroundColor: colors.neutral[200],
    borderRadius: 2,
    overflow: 'hidden',
  },
  progressFill: {
    height: 4,
    backgroundColor: colors.gold.base,
    borderRadius: 2,
  },
  successSlot: { alignItems: 'center', gap: spacing.sm },
  preview: { width: '100%', height: 150, borderRadius: radii.md },
  successBadgeRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.xs },
  successBadge: { fontSize: 13, color: colors.status.success, fontWeight: '600' },
  retakeButton: { padding: spacing.xs },
  retakeText: { fontSize: 13, color: colors.textSecondary, textDecorationLine: 'underline' },
  errorText: { fontSize: 12, color: colors.status.error, marginTop: spacing.xs },
});

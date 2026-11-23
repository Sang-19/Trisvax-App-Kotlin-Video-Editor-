export type ProjectCategory = 'ALL' | 'VIDEOS' | 'REELS' | 'DRAFTS';

export type AspectRatioPreset = 'PORTRAIT_9_16' | 'LANDSCAPE_16_9' | 'SQUARE_1_1';

export interface AspectPresetSpec {
  id: AspectRatioPreset;
  label: string;
  ratioWidth: number;
  ratioHeight: number;
  exportWidth: number;
  exportHeight: number;
}

export const ASPECT_PRESETS: Record<AspectRatioPreset, AspectPresetSpec> = {
  PORTRAIT_9_16: {
    id: 'PORTRAIT_9_16',
    label: '9:16',
    ratioWidth: 9,
    ratioHeight: 16,
    exportWidth: 1080,
    exportHeight: 1920,
  },
  LANDSCAPE_16_9: {
    id: 'LANDSCAPE_16_9',
    label: '16:9',
    ratioWidth: 16,
    ratioHeight: 9,
    exportWidth: 1920,
    exportHeight: 1080,
  },
  SQUARE_1_1: {
    id: 'SQUARE_1_1',
    label: '1:1',
    ratioWidth: 1,
    ratioHeight: 1,
    exportWidth: 1080,
    exportHeight: 1080,
  },
};

export interface CropConfig {
  preset: AspectRatioPreset;
  offsetX: number; // -1..1
  offsetY: number; // -1..1
  zoom: number; // 1..2.5
}

export interface VideoClipItem {
  id: string;
  sourceUrl: string;
  displayName: string;
  isImage: boolean;
  sourceDurationMs: number;
  startPositionMs: number;
  endPositionMs: number;
  timelinePositionMs: number;
  playbackSpeed: number;
  volume: number; // 0..2
  isMuted: boolean;
  rotationDegrees: number; // 0, 90, 180, 270
  cropConfig: CropConfig;
  thumbnails: string[];
  fileSizeBytes: number;
}

export interface MusicTrackItem {
  id: string;
  title: string;
  sourceUrl: string;
  isBundledAsset: boolean;
  sourceDurationMs: number;
  trimStartMs: number;
  trimEndMs: number;
  timelineStartMs: number;
  volume: number; // 0..1
  isMuted: boolean;
  loopToFitVideo: boolean;
}

export type TextAlignOption = 'left' | 'center' | 'right';

export interface TextOverlayItem {
  id: string;
  text: string;
  fontSizePx: number;
  colorHex: string;
  alignment: TextAlignOption;
  normalizedX: number; // 0..1
  normalizedY: number; // 0..1
  startTimeMs: number;
  endTimeMs: number;
}

export interface VoiceoverItem {
  id: string;
  title: string;
  sourceUrl: string;
  sourceDurationMs: number;
  trimStartMs: number;
  trimEndMs: number;
  timelineStartMs: number;
  volume: number; // 0..1
  isMuted: boolean;
}

export interface TimelineState {
  aspectRatio: AspectRatioPreset;
  clips: VideoClipItem[];
  musicTrack: MusicTrackItem | null;
  textOverlays: TextOverlayItem[];
  voiceovers: VoiceoverItem[];
}

export interface ProjectRecord {
  id: string;
  name: string;
  category: Exclude<ProjectCategory, 'ALL'>;
  thumbnailUrl: string;
  durationMs: number;
  storageSizeBytes: number;
  lastModifiedAt: number;
  createdAt: number;
  timeline: TimelineState;
}

export interface BundledMusicTrack {
  id: string;
  fileName: string;
  title: string;
  url: string;
  sizeBytes: number;
}

export interface DeviceMediaItem {
  id: string;
  url: string;
  displayName: string;
  isVideo: boolean;
  durationMs: number;
  thumbnailUrl: string;
  sizeBytes: number;
  mimeType: string;
}

export type EditorToolId =
  | 'none'
  | 'music'
  | 'text'
  | 'crop'
  | 'trim'
  | 'split'
  | 'speed'
  | 'volume'
  | 'rotate'
  | 'voice'
  | 'clips';

export interface ExportProgressState {
  status: 'idle' | 'rendering' | 'transcoding' | 'success' | 'error';
  progressPercent: number;
  message: string;
  downloadUrl?: string;
  downloadFileName?: string;
  errorMessage?: string;
}

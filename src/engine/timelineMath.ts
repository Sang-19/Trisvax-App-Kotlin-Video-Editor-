import type {
  AspectRatioPreset,
  CropConfig,
  TimelineState,
  VideoClipItem,
} from '../types/editor';

export function getClipEffectiveDurationMs(clip: VideoClipItem): number {
  const rawRange = Math.max(250, clip.endPositionMs - clip.startPositionMs);
  const speed = Math.max(0.25, Math.min(4, clip.playbackSpeed || 1));
  return Math.round(rawRange / speed);
}

export function getTimelineTotalDurationMs(timeline: TimelineState): number {
  return timeline.clips.reduce((acc, clip) => acc + getClipEffectiveDurationMs(clip), 0);
}

export function recomputeTimelinePositions(clips: VideoClipItem[]): VideoClipItem[] {
  let cursorMs = 0;
  return clips.map((clip) => {
    const updated: VideoClipItem = {
      ...clip,
      timelinePositionMs: cursorMs,
    };
    cursorMs += getClipEffectiveDurationMs(updated);
    return updated;
  });
}

export function splitClipAtPlayhead(
  clips: VideoClipItem[],
  playheadMs: number,
  generateId: () => string = () => `clip_${Math.random().toString(36).slice(2, 10)}`
): VideoClipItem[] {
  const reindexed = recomputeTimelinePositions(clips);
  const targetIndex = reindexed.findIndex((clip) => {
    const eff = getClipEffectiveDurationMs(clip);
    return (
      playheadMs > clip.timelinePositionMs + 250 &&
      playheadMs < clip.timelinePositionMs + eff - 250
    );
  });

  if (targetIndex === -1) return reindexed;

  const target = reindexed[targetIndex];
  const timelineOffset = playheadMs - target.timelinePositionMs;
  const sourceSplitMs = Math.round(
    target.startPositionMs + timelineOffset * target.playbackSpeed
  );

  if (
    sourceSplitMs <= target.startPositionMs + 250 ||
    sourceSplitMs >= target.endPositionMs - 250
  ) {
    return reindexed;
  }

  const firstHalf: VideoClipItem = {
    ...target,
    endPositionMs: sourceSplitMs,
  };
  const secondHalf: VideoClipItem = {
    ...target,
    id: generateId(),
    startPositionMs: sourceSplitMs,
  };

  const next = [...reindexed];
  next.splice(targetIndex, 1, firstHalf, secondHalf);
  return recomputeTimelinePositions(next);
}

export function trimClip(
  clips: VideoClipItem[],
  clipId: string,
  newStartMs: number,
  newEndMs: number
): VideoClipItem[] {
  const updated = clips.map((clip) => {
    if (clip.id !== clipId) return clip;
    const minSpan = 300;
    const maxSource = Math.max(minSpan, clip.sourceDurationMs);
    const clampedStart = Math.max(0, Math.min(newStartMs, maxSource - minSpan));
    const clampedEnd = Math.max(clampedStart + minSpan, Math.min(newEndMs, maxSource));
    return {
      ...clip,
      startPositionMs: Math.round(clampedStart),
      endPositionMs: Math.round(clampedEnd),
    };
  });
  return recomputeTimelinePositions(updated);
}

export function updateClipSpeed(
  clips: VideoClipItem[],
  clipId: string,
  speed: number
): VideoClipItem[] {
  const clamped = Math.max(0.5, Math.min(2.0, speed));
  const updated = clips.map((clip) =>
    clip.id === clipId ? { ...clip, playbackSpeed: clamped } : clip
  );
  return recomputeTimelinePositions(updated);
}

export function updateClipRotation(
  clips: VideoClipItem[],
  clipId: string,
  rotationDegrees: number
): VideoClipItem[] {
  const normalized = ((rotationDegrees % 360) + 360) % 360;
  return clips.map((clip) =>
    clip.id === clipId ? { ...clip, rotationDegrees: normalized } : clip
  );
}

export function updateClipVolume(
  clips: VideoClipItem[],
  clipId: string,
  volume: number,
  isMuted: boolean
): VideoClipItem[] {
  const clampedVol = Math.max(0, Math.min(2, volume));
  return clips.map((clip) =>
    clip.id === clipId ? { ...clip, volume: clampedVol, isMuted } : clip
  );
}

export function updateClipCrop(
  clips: VideoClipItem[],
  clipId: string,
  cropConfig: CropConfig
): VideoClipItem[] {
  return clips.map((clip) =>
    clip.id === clipId ? { ...clip, cropConfig } : clip
  );
}

export function applyAspectPresetToClips(
  clips: VideoClipItem[],
  preset: AspectRatioPreset
): VideoClipItem[] {
  return clips.map((clip) => ({
    ...clip,
    cropConfig: {
      ...clip.cropConfig,
      preset,
    },
  }));
}

export function reorderClip(
  clips: VideoClipItem[],
  fromIndex: number,
  toIndex: number
): VideoClipItem[] {
  if (
    fromIndex < 0 ||
    toIndex < 0 ||
    fromIndex >= clips.length ||
    toIndex >= clips.length ||
    fromIndex === toIndex
  ) {
    return clips;
  }
  const copy = [...clips];
  const [moved] = copy.splice(fromIndex, 1);
  copy.splice(toIndex, 0, moved);
  return recomputeTimelinePositions(copy);
}

export function deleteClip(clips: VideoClipItem[], clipId: string): VideoClipItem[] {
  if (clips.length <= 1) return clips;
  return recomputeTimelinePositions(clips.filter((c) => c.id !== clipId));
}

export function findClipAtPlayhead(
  clips: VideoClipItem[],
  playheadMs: number
): { clip: VideoClipItem; localSourceTimeMs: number; clipIndex: number } | null {
  if (clips.length === 0) return null;
  const reindexed = recomputeTimelinePositions(clips);
  for (let i = 0; i < reindexed.length; i++) {
    const clip = reindexed[i];
    const eff = getClipEffectiveDurationMs(clip);
    if (playheadMs < clip.timelinePositionMs + eff || i === reindexed.length - 1) {
      const timelineDelta = Math.max(0, playheadMs - clip.timelinePositionMs);
      const localSource = Math.min(
        clip.endPositionMs,
        clip.startPositionMs + timelineDelta * clip.playbackSpeed
      );
      return {
        clip,
        localSourceTimeMs: localSource,
        clipIndex: i,
      };
    }
  }
  return null;
}

export function formatDurationBadge(ms: number): string {
  const totalSec = Math.max(0, Math.floor(ms / 1000));
  const min = Math.floor(totalSec / 60);
  const sec = totalSec % 60;
  return `${String(min).padStart(2, '0')}:${String(sec).padStart(2, '0')}`;
}

export function formatShortTimecode(ms: number): string {
  const totalSec = Math.max(0, Math.floor(ms / 1000));
  const min = Math.floor(totalSec / 60);
  const sec = totalSec % 60;
  return `${min}:${String(sec).padStart(2, '0')}`;
}

export function formatStorageSize(bytes: number): string {
  if (!bytes || bytes <= 0) return '0 KB';
  const kb = bytes / 1024;
  if (kb < 1024) return `${Math.max(1, Math.round(kb))} KB`;
  const mb = kb / 1024;
  if (mb < 1024) return `${mb.toFixed(1)} MB`;
  const gb = mb / 1024;
  return `${gb.toFixed(1)} GB`;
}

export function formatRelativeDate(timestampMs: number, nowMs: number = Date.now()): string {
  const diff = Math.max(0, nowMs - timestampMs);
  const mins = Math.floor(diff / 60000);
  if (mins < 5) return 'Now';
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  return `${days}d ago`;
}

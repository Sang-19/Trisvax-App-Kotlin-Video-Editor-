import test from 'node:test';
import assert from 'node:assert/strict';
import {
  deleteClip,
  findClipAtPlayhead,
  formatDurationBadge,
  formatShortTimecode,
  formatStorageSize,
  getClipEffectiveDurationMs,
  getTimelineTotalDurationMs,
  reorderClip,
  splitClipAtPlayhead,
  trimClip,
  updateClipRotation,
  updateClipSpeed,
  updateClipVolume,
} from './timelineMath';
import type { VideoClipItem } from '../types/editor';

function makeClip(overrides: Partial<VideoClipItem> = {}): VideoClipItem {
  return {
    id: 'clip-1',
    sourceUrl: '/assets/samples/sunset_skyline.mp4',
    displayName: 'Sunset Skyline',
    isImage: false,
    sourceDurationMs: 10000,
    startPositionMs: 0,
    endPositionMs: 10000,
    timelinePositionMs: 0,
    playbackSpeed: 1,
    volume: 1,
    isMuted: false,
    rotationDegrees: 0,
    cropConfig: { preset: 'PORTRAIT_9_16', offsetX: 0, offsetY: 0, zoom: 1 },
    thumbnails: [],
    fileSizeBytes: 500000,
    ...overrides,
  };
}

test('effective duration accounts for trim and playback speed', () => {
  const clip = makeClip({ startPositionMs: 2000, endPositionMs: 8000, playbackSpeed: 2 });
  assert.equal(getClipEffectiveDurationMs(clip), 3000);

  const slowClip = makeClip({ startPositionMs: 0, endPositionMs: 4000, playbackSpeed: 0.5 });
  assert.equal(getClipEffectiveDurationMs(slowClip), 8000);
});

test('splitClipAtPlayhead splits selected clip into two contiguous segments', () => {
  const clips = [makeClip({ id: 'c1', startPositionMs: 0, endPositionMs: 8000 })];
  const split = splitClipAtPlayhead(clips, 3000, () => 'c2');
  assert.equal(split.length, 2);
  assert.equal(split[0].id, 'c1');
  assert.equal(split[0].startPositionMs, 0);
  assert.equal(split[0].endPositionMs, 3000);
  assert.equal(split[1].id, 'c2');
  assert.equal(split[1].startPositionMs, 3000);
  assert.equal(split[1].endPositionMs, 8000);
  assert.equal(split[1].timelinePositionMs, 3000);
});

test('splitClipAtPlayhead accounts for 2x playback speed when mapping timeline playhead to source time', () => {
  const clips = [makeClip({ id: 'c1', startPositionMs: 1000, endPositionMs: 9000, playbackSpeed: 2 })];
  // Effective duration is (8000 / 2) = 4000ms. Playhead at 1500ms = 3000ms into source from 1000ms -> 4000ms
  const split = splitClipAtPlayhead(clips, 1500, () => 'c2');
  assert.equal(split.length, 2);
  assert.equal(split[0].endPositionMs, 4000);
  assert.equal(split[1].startPositionMs, 4000);
  assert.equal(getTimelineTotalDurationMs({ aspectRatio: 'PORTRAIT_9_16', clips: split, musicTrack: null, textOverlays: [], voiceovers: [] }), 4000);
});

test('trimClip clamps boundaries and recomputes sequential timeline positions', () => {
  const clips = [
    makeClip({ id: 'c1', sourceDurationMs: 10000, startPositionMs: 0, endPositionMs: 10000 }),
    makeClip({ id: 'c2', sourceDurationMs: 5000, startPositionMs: 0, endPositionMs: 5000, timelinePositionMs: 10000 }),
  ];
  const trimmed = trimClip(clips, 'c1', 2000, 7000);
  assert.equal(trimmed[0].startPositionMs, 2000);
  assert.equal(trimmed[0].endPositionMs, 7000);
  assert.equal(trimmed[1].timelinePositionMs, 5000);
});

test('reorder, delete, rotate, speed, and volume update state cleanly', () => {
  let clips = [
    makeClip({ id: 'c1', endPositionMs: 4000 }),
    makeClip({ id: 'c2', endPositionMs: 6000 }),
  ];
  clips = reorderClip(clips, 0, 1);
  assert.equal(clips[0].id, 'c2');
  assert.equal(clips[1].id, 'c1');
  assert.equal(clips[1].timelinePositionMs, 6000);

  clips = updateClipSpeed(clips, 'c2', 1.5);
  assert.equal(clips[1].timelinePositionMs, 4000);

  clips = updateClipRotation(clips, 'c2', 270);
  assert.equal(clips[0].rotationDegrees, 270);

  clips = updateClipVolume(clips, 'c2', 1.6, false);
  assert.equal(clips[0].volume, 1.6);

  const lookup = findClipAtPlayhead(clips, 5000);
  assert.equal(lookup?.clip.id, 'c1');
  assert.equal(lookup?.localSourceTimeMs, 1000);

  // Deleting last remaining clip is prevented
  const afterDelete = deleteClip(clips, 'c2');
  assert.equal(afterDelete.length, 1);
  assert.equal(deleteClip(afterDelete, 'c1').length, 1);
});

test('timecode and storage formatting produce deterministic output', () => {
  assert.equal(formatDurationBadge(14000), '00:14');
  assert.equal(formatShortTimecode(65000), '1:05');
  assert.equal(formatStorageSize(477 * 1024), '477 KB');
  assert.equal(formatStorageSize(2.3 * 1024 * 1024), '2.3 MB');
});

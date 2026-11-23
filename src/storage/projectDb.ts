import type { DeviceMediaItem, ProjectRecord, TimelineState } from '../types/editor';
import { getTimelineTotalDurationMs } from '../engine/timelineMath';

const DB_NAME = 'trisvax_studio_local_db';
const DB_VERSION = 1;
const STORE_PROJECTS = 'projects';
const STORE_MEDIA_BLOBS = 'media_blobs';

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE_PROJECTS)) {
        db.createObjectStore(STORE_PROJECTS, { keyPath: 'id' });
      }
      if (!db.objectStoreNames.contains(STORE_MEDIA_BLOBS)) {
        db.createObjectStore(STORE_MEDIA_BLOBS, { keyPath: 'id' });
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

export async function saveMediaBlob(id: string, blob: Blob): Promise<void> {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_MEDIA_BLOBS, 'readwrite');
    tx.objectStore(STORE_MEDIA_BLOBS).put({ id, blob });
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

export async function getMediaBlobUrl(id: string): Promise<string | null> {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_MEDIA_BLOBS, 'readonly');
    const req = tx.objectStore(STORE_MEDIA_BLOBS).get(id);
    req.onsuccess = () => {
      const row = req.result as { id: string; blob: Blob } | undefined;
      resolve(row?.blob ? URL.createObjectURL(row.blob) : null);
    };
    req.onerror = () => reject(req.error);
  });
}

export async function listAllProjects(): Promise<ProjectRecord[]> {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_PROJECTS, 'readonly');
    const req = tx.objectStore(STORE_PROJECTS).getAll();
    req.onsuccess = () => {
      const items = (req.result as ProjectRecord[]) || [];
      items.sort((a, b) => b.lastModifiedAt - a.lastModifiedAt);
      resolve(items);
    };
    req.onerror = () => reject(req.error);
  });
}

export async function getProjectById(id: string): Promise<ProjectRecord | null> {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_PROJECTS, 'readonly');
    const req = tx.objectStore(STORE_PROJECTS).get(id);
    req.onsuccess = () => resolve((req.result as ProjectRecord) || null);
    req.onerror = () => reject(req.error);
  });
}

export async function upsertProject(project: ProjectRecord): Promise<void> {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_PROJECTS, 'readwrite');
    tx.objectStore(STORE_PROJECTS).put(project);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

export async function deleteProjectById(id: string): Promise<void> {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_PROJECTS, 'readwrite');
    tx.objectStore(STORE_PROJECTS).delete(id);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

export async function renameProjectById(id: string, newName: string): Promise<void> {
  const existing = await getProjectById(id);
  if (!existing) return;
  await upsertProject({
    ...existing,
    name: newName.trim() || 'Untitled',
    lastModifiedAt: Date.now(),
  });
}

export function inferProjectCategory(timeline: TimelineState): ProjectRecord['category'] {
  if (timeline.clips.length === 0) return 'DRAFTS';
  if (timeline.aspectRatio === 'PORTRAIT_9_16') return 'REELS';
  return 'VIDEOS';
}

export function computeTimelineStorageBytes(timeline: TimelineState): number {
  return timeline.clips.reduce((sum, clip) => sum + (clip.fileSizeBytes || 450000), 0);
}

export const BUNDLED_DEVICE_SAMPLES: DeviceMediaItem[] = [
  {
    id: 'sample_sunset_skyline',
    url: '/assets/samples/sunset_skyline.mp4',
    displayName: 'Sunset Skyline',
    isVideo: true,
    durationMs: 8000,
    thumbnailUrl: '',
    sizeBytes: 365000,
    mimeType: 'video/mp4',
  },
  {
    id: 'sample_window_light',
    url: '/assets/samples/window_light.mp4',
    displayName: 'Window Light',
    isVideo: true,
    durationMs: 9000,
    thumbnailUrl: '',
    sizeBytes: 424000,
    mimeType: 'video/mp4',
  },
  {
    id: 'sample_forest_road',
    url: '/assets/samples/forest_road.mp4',
    displayName: 'Forest Road',
    isVideo: true,
    durationMs: 10000,
    thumbnailUrl: '',
    sizeBytes: 456000,
    mimeType: 'video/mp4',
  },
];

export async function ensureSeededProjects(
  extractThumbs: (url: string, durationMs: number) => Promise<string[]>
): Promise<ProjectRecord[]> {
  const existing = await listAllProjects();
  if (existing.length > 0) return existing;

  const now = Date.now();
  const [sunsetThumbs, windowThumbs, forestThumbs] = await Promise.all([
    extractThumbs('/assets/samples/sunset_skyline.mp4', 8000),
    extractThumbs('/assets/samples/window_light.mp4', 9000),
    extractThumbs('/assets/samples/forest_road.mp4', 10000),
  ]);

  const seedProjects: ProjectRecord[] = [
    {
      id: 'proj_sunset_reel',
      name: 'Sunset Skyline',
      category: 'REELS',
      thumbnailUrl: sunsetThumbs[0] || '',
      durationMs: 17000,
      storageSizeBytes: 789000,
      lastModifiedAt: now - 1000 * 60 * 12,
      createdAt: now - 1000 * 60 * 60 * 24 * 2,
      timeline: {
        aspectRatio: 'PORTRAIT_9_16',
        clips: [
          {
            id: 'seed_c1',
            sourceUrl: '/assets/samples/sunset_skyline.mp4',
            displayName: 'Sunset Skyline',
            isImage: false,
            sourceDurationMs: 8000,
            startPositionMs: 0,
            endPositionMs: 8000,
            timelinePositionMs: 0,
            playbackSpeed: 1,
            volume: 1,
            isMuted: false,
            rotationDegrees: 0,
            cropConfig: { preset: 'PORTRAIT_9_16', offsetX: 0, offsetY: 0, zoom: 1 },
            thumbnails: sunsetThumbs,
            fileSizeBytes: 365000,
          },
          {
            id: 'seed_c2',
            sourceUrl: '/assets/samples/window_light.mp4',
            displayName: 'Window Light',
            isImage: false,
            sourceDurationMs: 9000,
            startPositionMs: 0,
            endPositionMs: 9000,
            timelinePositionMs: 8000,
            playbackSpeed: 1,
            volume: 1,
            isMuted: false,
            rotationDegrees: 0,
            cropConfig: { preset: 'PORTRAIT_9_16', offsetX: 0, offsetY: 0, zoom: 1 },
            thumbnails: windowThumbs,
            fileSizeBytes: 424000,
          },
        ],
        musicTrack: null,
        textOverlays: [],
        voiceovers: [],
      },
    },
    {
      id: 'proj_forest_drive',
      name: 'Running reel',
      category: 'VIDEOS',
      thumbnailUrl: forestThumbs[0] || '',
      durationMs: 10000,
      storageSizeBytes: 456000,
      lastModifiedAt: now - 1000 * 60 * 60 * 24 * 7,
      createdAt: now - 1000 * 60 * 60 * 24 * 7,
      timeline: {
        aspectRatio: 'LANDSCAPE_16_9',
        clips: [
          {
            id: 'seed_c3',
            sourceUrl: '/assets/samples/forest_road.mp4',
            displayName: 'Forest Road',
            isImage: false,
            sourceDurationMs: 10000,
            startPositionMs: 0,
            endPositionMs: 10000,
            timelinePositionMs: 0,
            playbackSpeed: 1,
            volume: 1,
            isMuted: false,
            rotationDegrees: 0,
            cropConfig: { preset: 'LANDSCAPE_16_9', offsetX: 0, offsetY: 0, zoom: 1 },
            thumbnails: forestThumbs,
            fileSizeBytes: 456000,
          },
        ],
        musicTrack: null,
        textOverlays: [],
        voiceovers: [],
      },
    },
    {
      id: 'proj_window_light',
      name: 'New project',
      category: 'REELS',
      thumbnailUrl: windowThumbs[0] || '',
      durationMs: 9000,
      storageSizeBytes: 424000,
      lastModifiedAt: now - 1000 * 60 * 2,
      createdAt: now - 1000 * 60 * 5,
      timeline: {
        aspectRatio: 'PORTRAIT_9_16',
        clips: [
          {
            id: 'seed_c4',
            sourceUrl: '/assets/samples/window_light.mp4',
            displayName: 'Window Light',
            isImage: false,
            sourceDurationMs: 9000,
            startPositionMs: 0,
            endPositionMs: 9000,
            timelinePositionMs: 0,
            playbackSpeed: 1,
            volume: 1,
            isMuted: false,
            rotationDegrees: 0,
            cropConfig: { preset: 'PORTRAIT_9_16', offsetX: 0, offsetY: 0, zoom: 1 },
            thumbnails: windowThumbs,
            fileSizeBytes: 424000,
          },
        ],
        musicTrack: null,
        textOverlays: [],
        voiceovers: [],
      },
    },
    {
      id: 'proj_draft_cut',
      name: 'Untitled',
      category: 'DRAFTS',
      thumbnailUrl: sunsetThumbs[1] || sunsetThumbs[0] || '',
      durationMs: 8000,
      storageSizeBytes: 365000,
      lastModifiedAt: now - 1000 * 60 * 60 * 24 * 5,
      createdAt: now - 1000 * 60 * 60 * 24 * 5,
      timeline: {
        aspectRatio: 'SQUARE_1_1',
        clips: [
          {
            id: 'seed_c5',
            sourceUrl: '/assets/samples/sunset_skyline.mp4',
            displayName: 'Sunset Skyline',
            isImage: false,
            sourceDurationMs: 8000,
            startPositionMs: 0,
            endPositionMs: 8000,
            timelinePositionMs: 0,
            playbackSpeed: 1,
            volume: 1,
            isMuted: false,
            rotationDegrees: 0,
            cropConfig: { preset: 'SQUARE_1_1', offsetX: 0, offsetY: 0, zoom: 1 },
            thumbnails: sunsetThumbs,
            fileSizeBytes: 365000,
          },
        ],
        musicTrack: null,
        textOverlays: [],
        voiceovers: [],
      },
    },
  ];

  for (const p of seedProjects) {
    p.durationMs = getTimelineTotalDurationMs(p.timeline);
    await upsertProject(p);
  }
  return seedProjects;
}

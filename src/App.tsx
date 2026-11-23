import React, { useEffect, useRef, useState } from 'react';
import {
  extractImageThumbnail,
  extractVideoMetadata,
  extractVideoThumbnails,
  exportTimelineTo1080pMp4,
} from './engine/videoMediaUtils';
import { getTimelineTotalDurationMs, recomputeTimelinePositions } from './engine/timelineMath';
import {
  computeTimelineStorageBytes,
  deleteProjectById,
  ensureSeededProjects,
  inferProjectCategory,
  listAllProjects,
  renameProjectById,
  saveMediaBlob,
  upsertProject,
} from './storage/projectDb';
import { CameraCaptureScreen } from './screens/CameraCaptureScreen';
import { EditorScreen } from './screens/EditorScreen';
import {
  ExportProgressModal,
  SaveConfirmationScreen,
} from './screens/ExportAndFinish';
import { MediaPickerScreen } from './screens/MediaPickerScreen';
import { ProjectsScreen } from './screens/ProjectsScreen';
import { WelcomeScreen } from './screens/WelcomeScreen';
import type {
  DeviceMediaItem,
  ExportProgressState,
  ProjectRecord,
  TimelineState,
  VideoClipItem,
} from './types/editor';

type AppRoute =
  | { name: 'welcome' }
  | { name: 'projects' }
  | { name: 'picker' }
  | { name: 'camera' }
  | { name: 'editor'; projectId: string }
  | { name: 'finished'; downloadUrl?: string; downloadFileName?: string };

export default function App() {
  const [route, setRoute] = useState<AppRoute>(() => {
    const params = new URLSearchParams(window.location.search);
    return params.get('autostart') === '1' ? { name: 'projects' } : { name: 'welcome' };
  });

  const [projects, setProjects] = useState<ProjectRecord[]>([]);
  const [exportState, setExportState] = useState<ExportProgressState>({
    status: 'idle',
    progressPercent: 0,
    message: '',
  });
  const [exportAspect, setExportAspect] = useState<TimelineState['aspectRatio']>('PORTRAIT_9_16');
  const exportAbortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    ensureSeededProjects((url, dur) => extractVideoThumbnails(url, dur, 4)).then(
      (seeded) => setProjects(seeded)
    );
  }, []);

  const refreshProjects = async () => {
    const all = await listAllProjects();
    setProjects(all);
  };

  const createProjectFromMediaItems = async (
    items: DeviceMediaItem[],
    defaultTitle: string = 'New project'
  ) => {
    const clips: VideoClipItem[] = items.map((item) => ({
      id: `clip_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
      sourceUrl: item.url,
      displayName: item.displayName,
      isImage: !item.isVideo,
      sourceDurationMs: item.durationMs,
      startPositionMs: 0,
      endPositionMs: item.durationMs,
      timelinePositionMs: 0,
      playbackSpeed: 1,
      volume: 1,
      isMuted: false,
      rotationDegrees: 0,
      cropConfig: { preset: 'PORTRAIT_9_16', offsetX: 0, offsetY: 0, zoom: 1 },
      thumbnails: item.thumbnailUrl ? [item.thumbnailUrl] : [],
      fileSizeBytes: item.sizeBytes,
    }));

    const reindexed = recomputeTimelinePositions(clips);
    const timeline: TimelineState = {
      aspectRatio: 'PORTRAIT_9_16',
      clips: reindexed,
      musicTrack: null,
      textOverlays: [],
      voiceovers: [],
    };
    const now = Date.now();
    const record: ProjectRecord = {
      id: `proj_${now}`,
      name: defaultTitle,
      category: inferProjectCategory(timeline),
      thumbnailUrl: reindexed[0]?.thumbnails[0] || '',
      durationMs: getTimelineTotalDurationMs(timeline),
      storageSizeBytes: computeTimelineStorageBytes(timeline),
      lastModifiedAt: now,
      createdAt: now,
      timeline,
    };

    await upsertProject(record);
    await refreshProjects();
    setRoute({ name: 'editor', projectId: record.id });
  };

  const handleQuickImportFiles = async (files: FileList) => {
    const converted: DeviceMediaItem[] = [];
    for (const file of Array.from(files)) {
      const id = `file_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`;
      await saveMediaBlob(id, file);
      const url = URL.createObjectURL(file);
      const isVideo = file.type.startsWith('video/');
      if (isVideo) {
        const meta = await extractVideoMetadata(url);
        const thumbs = await extractVideoThumbnails(url, meta.durationMs, 4);
        converted.push({
          id,
          url,
          displayName: file.name,
          isVideo: true,
          durationMs: meta.durationMs,
          thumbnailUrl: thumbs[0] || '',
          sizeBytes: file.size,
          mimeType: file.type,
        });
      } else {
        const thumb = await extractImageThumbnail(url);
        converted.push({
          id,
          url,
          displayName: file.name,
          isVideo: false,
          durationMs: 3000,
          thumbnailUrl: thumb,
          sizeBytes: file.size,
          mimeType: file.type,
        });
      }
    }
    if (converted.length > 0) {
      await createProjectFromMediaItems(converted, converted[0].displayName.replace(/\.[^.]+$/, ''));
    }
  };

  const handleSaveProjectState = async (updated: ProjectRecord) => {
    await upsertProject(updated);
    setProjects((prev) => {
      const others = prev.filter((p) => p.id !== updated.id);
      return [updated, ...others].sort((a, b) => b.lastModifiedAt - a.lastModifiedAt);
    });
  };

  const handleStartExport = async (timeline: TimelineState) => {
    if (timeline.clips.length === 0) return;
    const controller = new AbortController();
    exportAbortRef.current = controller;
    setExportAspect(timeline.aspectRatio);
    setExportState({
      status: 'rendering',
      progressPercent: 4,
      message: 'Preparing 1080p MP4 export...',
    });

    try {
      const result = await exportTimelineTo1080pMp4(
        timeline,
        (progressPercent, message) => {
          setExportState({
            status: progressPercent >= 75 ? 'transcoding' : 'rendering',
            progressPercent,
            message,
          });
        },
        controller.signal
      );

      setExportState({
        status: 'success',
        progressPercent: 100,
        message: 'Saved',
        downloadUrl: result.blobUrl,
        downloadFileName: result.fileName,
      });
      setRoute({
        name: 'finished',
        downloadUrl: result.blobUrl,
        downloadFileName: result.fileName,
      });
    } catch (err) {
      if (controller.signal.aborted) {
        setExportState({ status: 'idle', progressPercent: 0, message: '' });
      } else {
        setExportState({
          status: 'error',
          progressPercent: 0,
          message: '',
          errorMessage: err instanceof Error ? err.message : 'Export failed.',
        });
      }
    }
  };

  const activeProject =
    route.name === 'editor'
      ? projects.find((p) => p.id === route.projectId) || null
      : null;

  return (
    <>
      {route.name === 'welcome' && (
        <WelcomeScreen onOpenStudio={() => setRoute({ name: 'projects' })} />
      )}

      {route.name === 'projects' && (
        <ProjectsScreen
          projects={projects}
          onOpenProject={(projectId) => setRoute({ name: 'editor', projectId })}
          onRenameProject={async (id, newName) => {
            await renameProjectById(id, newName);
            await refreshProjects();
          }}
          onDeleteProject={async (id) => {
            await deleteProjectById(id);
            await refreshProjects();
          }}
          onQuickImportFiles={handleQuickImportFiles}
          onOpenCamera={() => setRoute({ name: 'camera' })}
          onOpenGallery={() => setRoute({ name: 'picker' })}
        />
      )}

      {route.name === 'picker' && (
        <MediaPickerScreen
          onBack={() => setRoute({ name: 'projects' })}
          onContinue={(selected) => createProjectFromMediaItems(selected)}
        />
      )}

      {route.name === 'camera' && (
        <CameraCaptureScreen
          onBack={() => setRoute({ name: 'projects' })}
          onCaptured={(item) => createProjectFromMediaItems([item], 'Camera clip')}
        />
      )}

      {route.name === 'editor' && activeProject && (
        <EditorScreen
          project={activeProject}
          onSaveProject={handleSaveProjectState}
          onClose={() => setRoute({ name: 'projects' })}
          onExport={handleStartExport}
        />
      )}

      {route.name === 'finished' && (
        <SaveConfirmationScreen
          downloadUrl={route.downloadUrl}
          downloadFileName={route.downloadFileName}
          onGoToHome={() => {
            setExportState({ status: 'idle', progressPercent: 0, message: '' });
            setRoute({ name: 'projects' });
          }}
        />
      )}

      <ExportProgressModal
        state={exportState}
        aspectRatio={exportAspect}
        onCancel={() => {
          exportAbortRef.current?.abort();
          setExportState({ status: 'idle', progressPercent: 0, message: '' });
        }}
        onDismissError={() =>
          setExportState({ status: 'idle', progressPercent: 0, message: '' })
        }
      />
    </>
  );
}

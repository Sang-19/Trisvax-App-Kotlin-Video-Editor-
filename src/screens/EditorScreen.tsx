import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  ArrowLeftRight,
  Crop,
  Download,
  FastForward,
  Mic,
  Music,
  Pause,
  Play,
  Plus,
  RotateCw,
  Scissors,
  SlidersHorizontal,
  Type,
  Volume2,
  X,
} from 'lucide-react';
import { EditorToolPanel } from '../components/EditorToolPanel';
import { TimelineTracks } from '../components/TimelineTracks';
import {
  applyAspectPresetToClips,
  deleteClip,
  findClipAtPlayhead,
  formatShortTimecode,
  getTimelineTotalDurationMs,
  reorderClip,
  recomputeTimelinePositions,
  splitClipAtPlayhead,
  trimClip,
  updateClipCrop,
  updateClipRotation,
  updateClipSpeed,
  updateClipVolume,
} from '../engine/timelineMath';
import {
  extractImageThumbnail,
  extractVideoMetadata,
  extractVideoThumbnails,
} from '../engine/videoMediaUtils';
import { saveMediaBlob } from '../storage/projectDb';
import {
  ASPECT_PRESETS,
  type BundledMusicTrack,
  type EditorToolId,
  type ProjectRecord,
  type TimelineState,
  type VideoClipItem,
} from '../types/editor';

const TOOLS: { id: EditorToolId | 'save'; label: string; icon: React.ComponentType<{ className?: string }> }[] = [
  { id: 'music', label: 'Music', icon: Music },
  { id: 'text', label: 'Text', icon: Type },
  { id: 'crop', label: 'Crop', icon: Crop },
  { id: 'trim', label: 'Trim', icon: SlidersHorizontal },
  { id: 'split', label: 'Split', icon: Scissors },
  { id: 'speed', label: 'Speed', icon: FastForward },
  { id: 'volume', label: 'Volume', icon: Volume2 },
  { id: 'rotate', label: 'Rotate', icon: RotateCw },
  { id: 'voice', label: 'Voice', icon: Mic },
  { id: 'clips', label: 'Clips', icon: ArrowLeftRight },
  { id: 'save', label: 'Save', icon: Download },
];

export function EditorScreen({
  project,
  onSaveProject,
  onClose,
  onExport,
}: {
  project: ProjectRecord;
  onSaveProject: (updated: ProjectRecord) => void;
  onClose: () => void;
  onExport: (timeline: TimelineState) => void;
}) {
  const [timeline, setTimeline] = useState<TimelineState>(project.timeline);
  const [selectedClipId, setSelectedClipId] = useState<string | null>(
    project.timeline.clips[0]?.id || null
  );
  const [selectedTextId, setSelectedTextId] = useState<string>('');
  const [playheadMs, setPlayheadMs] = useState(0);
  const [isPlaying, setIsPlaying] = useState(false);
  const [activeTool, setActiveTool] = useState<EditorToolId>('none');
  const [bundledMusic, setBundledMusic] = useState<BundledMusicTrack[]>([]);

  const videoRef = useRef<HTMLVideoElement | null>(null);
  const musicAudioRef = useRef<HTMLAudioElement | null>(null);
  const voiceAudioRef = useRef<HTMLAudioElement | null>(null);
  const addClipsInputRef = useRef<HTMLInputElement | null>(null);
  const playLoopRef = useRef<number | null>(null);
  const lastFrameRef = useRef<number>(0);

  useEffect(() => {
    setTimeline(project.timeline);
    setSelectedClipId(project.timeline.clips[0]?.id || null);
    setPlayheadMs(0);
    setIsPlaying(false);
  }, [project.id]);

  useEffect(() => {
    fetch('/api/music-catalog')
      .then((r) => r.json())
      .then((data) => {
        if (Array.isArray(data.tracks)) setBundledMusic(data.tracks);
      })
      .catch(() => {});
  }, []);

  const totalDurationMs = useMemo(() => getTimelineTotalDurationMs(timeline), [timeline]);
  const playheadLookup = useMemo(
    () => findClipAtPlayhead(timeline.clips, playheadMs),
    [timeline.clips, playheadMs]
  );

  const activeClip = playheadLookup?.clip || timeline.clips[0] || null;
  const selectedClip =
    timeline.clips.find((c) => c.id === selectedClipId) || activeClip;

  const commitTimeline = (nextTimeline: TimelineState) => {
    setTimeline(nextTimeline);
    const dur = getTimelineTotalDurationMs(nextTimeline);
    onSaveProject({
      ...project,
      durationMs: dur,
      thumbnailUrl: nextTimeline.clips[0]?.thumbnails[0] || project.thumbnailUrl,
      category:
        nextTimeline.clips.length === 0
          ? 'DRAFTS'
          : nextTimeline.aspectRatio === 'PORTRAIT_9_16'
            ? 'REELS'
            : 'VIDEOS',
      lastModifiedAt: Date.now(),
      timeline: nextTimeline,
    });
  };

  // Sync HTML5 video preview element when source or local position changes
  useEffect(() => {
    const vid = videoRef.current;
    if (!vid || !activeClip || activeClip.isImage) return;

    const targetSec = (playheadLookup?.localSourceTimeMs || activeClip.startPositionMs) / 1000;
    if (vid.getAttribute('data-src-url') !== activeClip.sourceUrl) {
      vid.setAttribute('data-src-url', activeClip.sourceUrl);
      vid.src = activeClip.sourceUrl;
      vid.load();
    }

    vid.playbackRate = activeClip.playbackSpeed;
    vid.muted = activeClip.isMuted || activeClip.volume <= 0.01;
    vid.volume = Math.min(1, Math.max(0, activeClip.volume));

    if (!isPlaying && Math.abs(vid.currentTime - targetSec) > 0.12) {
      try {
        vid.currentTime = targetSec;
      } catch {
        // ignore seek before metadata
      }
    }
  }, [activeClip?.id, activeClip?.sourceUrl, activeClip?.playbackSpeed, activeClip?.isMuted, activeClip?.volume, playheadLookup?.localSourceTimeMs, isPlaying]);

  // Playback tick loop driving multi-clip timeline + music + voiceover
  useEffect(() => {
    if (!isPlaying) {
      if (playLoopRef.current) cancelAnimationFrame(playLoopRef.current);
      videoRef.current?.pause();
      musicAudioRef.current?.pause();
      voiceAudioRef.current?.pause();
      return;
    }

    videoRef.current?.play().catch(() => {});

    if (timeline.musicTrack && !timeline.musicTrack.isMuted && musicAudioRef.current) {
      const m = musicAudioRef.current;
      m.volume = timeline.musicTrack.volume;
      m.play().catch(() => {});
    }

    if (timeline.voiceovers[0] && !timeline.voiceovers[0].isMuted && voiceAudioRef.current) {
      const v = voiceAudioRef.current;
      v.volume = timeline.voiceovers[0].volume;
      if (playheadMs >= timeline.voiceovers[0].timelineStartMs) {
        v.play().catch(() => {});
      }
    }

    lastFrameRef.current = performance.now();
    const step = (now: number) => {
      const dt = now - lastFrameRef.current;
      lastFrameRef.current = now;

      setPlayheadMs((prev) => {
        const next = prev + dt;
        if (next >= totalDurationMs) {
          setIsPlaying(false);
          return 0;
        }
        return next;
      });
      playLoopRef.current = requestAnimationFrame(step);
    };
    playLoopRef.current = requestAnimationFrame(step);

    return () => {
      if (playLoopRef.current) cancelAnimationFrame(playLoopRef.current);
    };
  }, [isPlaying, totalDurationMs]);

  const handleSeek = (ms: number) => {
    const clamped = Math.max(0, Math.min(totalDurationMs, ms));
    setPlayheadMs(clamped);
    const lookup = findClipAtPlayhead(timeline.clips, clamped);
    if (lookup) {
      setSelectedClipId(lookup.clip.id);
      if (videoRef.current && !lookup.clip.isImage) {
        try {
          videoRef.current.currentTime = lookup.localSourceTimeMs / 1000;
        } catch {
          // ignore
        }
      }
    }
  };

  const handleAddMoreFiles = async (files: FileList) => {
    const appended: VideoClipItem[] = [];
    for (const file of Array.from(files)) {
      const id = `clip_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`;
      await saveMediaBlob(id, file);
      const url = URL.createObjectURL(file);
      const isImage = file.type.startsWith('image/');
      if (isImage) {
        const thumb = await extractImageThumbnail(url);
        appended.push({
          id,
          sourceUrl: url,
          displayName: file.name,
          isImage: true,
          sourceDurationMs: 3000,
          startPositionMs: 0,
          endPositionMs: 3000,
          timelinePositionMs: 0,
          playbackSpeed: 1,
          volume: 1,
          isMuted: false,
          rotationDegrees: 0,
          cropConfig: { preset: timeline.aspectRatio, offsetX: 0, offsetY: 0, zoom: 1 },
          thumbnails: [thumb],
          fileSizeBytes: file.size,
        });
      } else {
        const meta = await extractVideoMetadata(url);
        const thumbs = await extractVideoThumbnails(url, meta.durationMs, 4);
        appended.push({
          id,
          sourceUrl: url,
          displayName: file.name,
          isImage: false,
          sourceDurationMs: meta.durationMs,
          startPositionMs: 0,
          endPositionMs: meta.durationMs,
          timelinePositionMs: 0,
          playbackSpeed: 1,
          volume: 1,
          isMuted: false,
          rotationDegrees: 0,
          cropConfig: { preset: timeline.aspectRatio, offsetX: 0, offsetY: 0, zoom: 1 },
          thumbnails: thumbs,
          fileSizeBytes: file.size,
        });
      }
    }
    if (appended.length > 0) {
      const merged = recomputeTimelinePositions([...timeline.clips, ...appended]);
      commitTimeline({ ...timeline, clips: merged });
      setSelectedClipId(appended[0].id);
    }
  };

  const aspectSpec = ASPECT_PRESETS[timeline.aspectRatio];

  return (
    <div className="flex min-h-screen w-full flex-col bg-[#0B0807] text-white">
      {/* Hidden synchronized audio tracks */}
      {timeline.musicTrack && (
        <audio
          ref={musicAudioRef}
          src={timeline.musicTrack.sourceUrl}
          loop={timeline.musicTrack.loopToFitVideo}
        />
      )}
      {timeline.voiceovers[0] && (
        <audio ref={voiceAudioRef} src={timeline.voiceovers[0].sourceUrl} />
      )}

      {/* Top Video Preview Canvas matching Editor_Page screenshots */}
      <div className="relative flex flex-1 items-center justify-center px-16 py-3 min-h-[280px] max-h-[52vh]">
        <button
          type="button"
          onClick={() => {
            setIsPlaying(false);
            onClose();
          }}
          aria-label="Close editor"
          className="absolute left-3 top-4 z-20 flex h-11 w-11 items-center justify-center rounded-full border border-[#2B352D] bg-[#221711] text-white hover:border-[#FF5500]"
        >
          <X className="h-5 w-5" />
        </button>

        <button
          type="button"
          onClick={() => {
            setIsPlaying(false);
            onExport(timeline);
          }}
          className="absolute right-3 top-4 z-20 min-h-[42px] rounded-full border border-[#2B352D] bg-[#221711] px-4 py-2 text-sm font-semibold text-white hover:border-[#FF5500] transition-colors"
        >
          Next
        </button>

        {/* Aspect-ratio video container */}
        <div
          onClick={() => setIsPlaying((p) => !p)}
          style={{
            aspectRatio: `${aspectSpec.ratioWidth} / ${aspectSpec.ratioHeight}`,
          }}
          className="relative flex h-full max-h-[46vh] max-w-full cursor-pointer items-center justify-center overflow-hidden rounded-[12px] border border-[#2B352D] bg-black"
        >
          {activeClip ? (
            activeClip.isImage ? (
              <img
                src={activeClip.sourceUrl}
                alt={activeClip.displayName}
                referrerPolicy="no-referrer"
                style={{
                  transform: `rotate(${activeClip.rotationDegrees}deg) scale(${activeClip.cropConfig.zoom}) translate(${activeClip.cropConfig.offsetX * 15}%, ${activeClip.cropConfig.offsetY * 15}%)`,
                }}
                className="h-full w-full object-cover transition-transform duration-150"
              />
            ) : (
              <video
                ref={videoRef}
                playsInline
                style={{
                  transform: `rotate(${activeClip.rotationDegrees}deg) scale(${activeClip.cropConfig.zoom}) translate(${activeClip.cropConfig.offsetX * 15}%, ${activeClip.cropConfig.offsetY * 15}%)`,
                }}
                className="h-full w-full object-cover transition-transform duration-150"
              />
            )
          ) : (
            <p className="text-xs text-[#9AA69B]">No clips in timeline</p>
          )}

          {/* Active text overlays rendered live over video */}
          {timeline.textOverlays.map((ov) => {
            if (playheadMs < ov.startTimeMs || playheadMs > ov.endTimeMs) return null;
            return (
              <button
                key={ov.id}
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  setSelectedTextId(ov.id);
                  setActiveTool('text');
                }}
                style={{
                  top: `${ov.normalizedY * 100}%`,
                  color: ov.colorHex,
                  fontSize: `${ov.fontSizePx * 0.72}px`,
                  textAlign: ov.alignment,
                }}
                className="absolute left-3 right-3 -translate-y-1/2 font-bold drop-shadow-[0_2px_6px_rgba(0,0,0,0.85)]"
              >
                {ov.text}
              </button>
            );
          })}

          {!isPlaying && (
            <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
              <span className="flex h-14 w-14 items-center justify-center rounded-full bg-black/55 text-white">
                <Play className="ml-0.5 h-7 w-7 fill-white" />
              </span>
            </div>
          )}
        </div>
      </div>

      {/* Transport bar matching Editor_Page screenshots */}
      <div className="flex items-center justify-between border-t border-[#2B352D] px-4 py-2.5">
        <button
          type="button"
          onClick={() => setIsPlaying((p) => !p)}
          aria-label={isPlaying ? 'Pause video' : 'Play video'}
          className="flex h-11 w-11 items-center justify-center rounded-full border border-[#2B352D] bg-[#221711] text-white hover:border-[#FF5500]"
        >
          {isPlaying ? <Pause className="h-5 w-5" /> : <Play className="ml-0.5 h-5 w-5" />}
        </button>

        <div className="text-[15px] font-bold font-mono-tabular">
          <span className="text-white">{formatShortTimecode(playheadMs)}</span>
          <span className="text-[#9AA69B]"> / {formatShortTimecode(totalDurationMs)}</span>
        </div>

        <button
          type="button"
          onClick={() => {
            if (activeTool !== 'none') {
              setActiveTool('none');
            } else {
              addClipsInputRef.current?.click();
            }
          }}
          aria-label={activeTool !== 'none' ? 'Close active tool' : 'Add clip to timeline'}
          className="flex h-11 w-11 items-center justify-center rounded-full border border-[#2B352D] bg-[#221711] text-white hover:border-[#FF5500]"
        >
          {activeTool !== 'none' ? <X className="h-5 w-5" /> : <Plus className="h-5 w-5" />}
        </button>
      </div>

      {/* Multi-Track Timeline */}
      <TimelineTracks
        timeline={timeline}
        selectedClipId={selectedClip?.id || null}
        playheadMs={playheadMs}
        onSeek={handleSeek}
        onSelectClip={(id) => setSelectedClipId(id)}
        onToggleClipMute={(clipId) => {
          const target = timeline.clips.find((c) => c.id === clipId);
          if (!target) return;
          commitTimeline({
            ...timeline,
            clips: updateClipVolume(timeline.clips, clipId, target.volume, !target.isMuted),
          });
        }}
        onSelectTool={(tool) => setActiveTool(tool)}
        onSelectTextOverlay={(id) => setSelectedTextId(id)}
      />

      {/* Contextual Editing Tool Controls */}
      <EditorToolPanel
        activeTool={activeTool}
        timeline={timeline}
        selectedClip={selectedClip}
        selectedTextId={selectedTextId}
        playheadMs={playheadMs}
        bundledMusic={bundledMusic}
        onClose={() => setActiveTool('none')}
        onTrimClip={(clipId, startMs, endMs) =>
          commitTimeline({ ...timeline, clips: trimClip(timeline.clips, clipId, startMs, endMs) })
        }
        onSplitAtPlayhead={() =>
          commitTimeline({
            ...timeline,
            clips: splitClipAtPlayhead(timeline.clips, playheadMs),
          })
        }
        onChangeAspectAndCrop={(preset, zoom, offsetX, offsetY) => {
          let nextClips = applyAspectPresetToClips(timeline.clips, preset);
          if (selectedClip) {
            nextClips = updateClipCrop(nextClips, selectedClip.id, {
              preset,
              zoom,
              offsetX,
              offsetY,
            });
          }
          commitTimeline({ ...timeline, aspectRatio: preset, clips: nextClips });
        }}
        onRotateClip={(clipId) => {
          const c = timeline.clips.find((item) => item.id === clipId);
          if (!c) return;
          commitTimeline({
            ...timeline,
            clips: updateClipRotation(timeline.clips, clipId, c.rotationDegrees + 90),
          });
        }}
        onChangeSpeed={(clipId, speed) =>
          commitTimeline({
            ...timeline,
            clips: updateClipSpeed(timeline.clips, clipId, speed),
          })
        }
        onChangeVolume={(clipId, vol, muted) =>
          commitTimeline({
            ...timeline,
            clips: updateClipVolume(timeline.clips, clipId, vol, muted),
          })
        }
        onReorderClip={(fromIdx, toIdx) =>
          commitTimeline({
            ...timeline,
            clips: reorderClip(timeline.clips, fromIdx, toIdx),
          })
        }
        onDeleteClip={(clipId) => {
          const nextClips = deleteClip(timeline.clips, clipId);
          setSelectedClipId(nextClips[0]?.id || null);
          commitTimeline({ ...timeline, clips: nextClips });
        }}
        onSelectMusic={(track) => commitTimeline({ ...timeline, musicTrack: track })}
        onUpdateMusic={(patch) => {
          if (!timeline.musicTrack) return;
          commitTimeline({
            ...timeline,
            musicTrack: { ...timeline.musicTrack, ...patch },
          });
        }}
        onRemoveMusic={() => commitTimeline({ ...timeline, musicTrack: null })}
        onUpsertText={(overlay) => {
          const exists = timeline.textOverlays.some((t) => t.id === overlay.id);
          const nextOverlays = exists
            ? timeline.textOverlays.map((t) => (t.id === overlay.id ? overlay : t))
            : [...timeline.textOverlays, overlay];
          setSelectedTextId(overlay.id);
          commitTimeline({ ...timeline, textOverlays: nextOverlays });
        }}
        onDeleteText={(id) => {
          setSelectedTextId('');
          commitTimeline({
            ...timeline,
            textOverlays: timeline.textOverlays.filter((t) => t.id !== id),
          });
        }}
        onSetVoiceover={(vo) => commitTimeline({ ...timeline, voiceovers: [vo] })}
        onDeleteVoiceover={() => commitTimeline({ ...timeline, voiceovers: [] })}
      />

      {/* Bottom Circular Tool Toolbar matching Editor_Page_73a362d7, Editor_Page_2, Editor_Page_3 */}
      <footer className="mt-auto px-3 py-2.5">
        <div className="mx-auto flex max-w-4xl items-center gap-2 overflow-x-auto pb-1 sm:justify-center">
          {TOOLS.map((tool) => {
            const Icon = tool.icon;
            const active = tool.id !== 'save' && activeTool === tool.id;
            return (
              <button
                key={tool.id}
                type="button"
                onClick={() => {
                  if (tool.id === 'save') {
                    setIsPlaying(false);
                    onExport(timeline);
                  } else {
                    const nextTool = tool.id;
                    setActiveTool((prev) => (prev === nextTool ? 'none' : nextTool));
                  }
                }}
                className="flex shrink-0 flex-col items-center gap-1.5 rounded-xl px-2 py-1 focus-visible:outline-2 focus-visible:outline-[#FF5500]"
              >
                <span
                  className={`flex h-12 w-12 items-center justify-center rounded-full border transition-colors ${
                    active
                      ? 'border-[#FF8232] bg-[#FF5500] text-white'
                      : 'border-[#2B352D] bg-[#18110D] text-white hover:border-[#FF5500]'
                  }`}
                >
                  <Icon className="h-5 w-5" />
                </span>
                <span
                  className={`text-[11px] font-medium whitespace-nowrap ${
                    active ? 'text-[#FF8232]' : 'text-white'
                  }`}
                >
                  {tool.label}
                </span>
              </button>
            );
          })}
        </div>
      </footer>

      <input
        ref={addClipsInputRef}
        type="file"
        accept="video/*,image/*"
        multiple
        className="hidden"
        onChange={(e) => {
          if (e.target.files && e.target.files.length > 0) {
            handleAddMoreFiles(e.target.files);
          }
          e.target.value = '';
        }}
      />
    </div>
  );
}

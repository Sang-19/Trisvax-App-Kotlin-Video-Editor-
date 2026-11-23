import React, { useEffect, useRef, useState } from 'react';
import {
  ArrowLeft,
  ArrowRight,
  Mic,
  Music,
  RotateCw,
  Scissors,
  Square,
  Trash2,
  Upload,
  Volume2,
  VolumeX,
} from 'lucide-react';
import { formatDurationBadge, getTimelineTotalDurationMs } from '../engine/timelineMath';
import {
  ASPECT_PRESETS,
  type AspectRatioPreset,
  type BundledMusicTrack,
  type EditorToolId,
  type MusicTrackItem,
  type TextAlignOption,
  type TextOverlayItem,
  type TimelineState,
  type VideoClipItem,
  type VoiceoverItem,
} from '../types/editor';

export function EditorToolPanel({
  activeTool,
  timeline,
  selectedClip,
  selectedTextId,
  playheadMs,
  bundledMusic,
  onClose,
  onTrimClip,
  onSplitAtPlayhead,
  onChangeAspectAndCrop,
  onRotateClip,
  onChangeSpeed,
  onChangeVolume,
  onReorderClip,
  onDeleteClip,
  onSelectMusic,
  onUpdateMusic,
  onRemoveMusic,
  onUpsertText,
  onDeleteText,
  onSetVoiceover,
  onDeleteVoiceover,
}: {
  activeTool: EditorToolId;
  timeline: TimelineState;
  selectedClip: VideoClipItem | null;
  selectedTextId: string;
  playheadMs: number;
  bundledMusic: BundledMusicTrack[];
  onClose: () => void;
  onTrimClip: (clipId: string, startMs: number, endMs: number) => void;
  onSplitAtPlayhead: () => void;
  onChangeAspectAndCrop: (preset: AspectRatioPreset, zoom: number, offsetX: number, offsetY: number) => void;
  onRotateClip: (clipId: string) => void;
  onChangeSpeed: (clipId: string, speed: number) => void;
  onChangeVolume: (clipId: string, volume: number, muted: boolean) => void;
  onReorderClip: (fromIdx: number, toIdx: number) => void;
  onDeleteClip: (clipId: string) => void;
  onSelectMusic: (track: MusicTrackItem) => void;
  onUpdateMusic: (patch: Partial<MusicTrackItem>) => void;
  onRemoveMusic: () => void;
  onUpsertText: (overlay: TextOverlayItem) => void;
  onDeleteText: (id: string) => void;
  onSetVoiceover: (vo: VoiceoverItem) => void;
  onDeleteVoiceover: () => void;
}) {
  const totalDurationMs = Math.max(1000, getTimelineTotalDurationMs(timeline));
  const audioInputRef = useRef<HTMLInputElement | null>(null);

  // Text editor state
  const existingText = timeline.textOverlays.find((t) => t.id === selectedTextId) || null;
  const [textInput, setTextInput] = useState(existingText?.text || 'TRISVAX');
  const [textSize, setTextSize] = useState(existingText?.fontSizePx || 28);
  const [textColor, setTextColor] = useState(existingText?.colorHex || '#FFFFFF');
  const [textAlign, setTextAlign] = useState<TextAlignOption>(existingText?.alignment || 'center');
  const [textY, setTextY] = useState(existingText?.normalizedY || 0.5);
  const [textStart, setTextStart] = useState(existingText?.startTimeMs ?? playheadMs);
  const [textEnd, setTextEnd] = useState(
    existingText?.endTimeMs ?? Math.min(totalDurationMs, playheadMs + 3500)
  );

  useEffect(() => {
    if (existingText) {
      setTextInput(existingText.text);
      setTextSize(existingText.fontSizePx);
      setTextColor(existingText.colorHex);
      setTextAlign(existingText.alignment);
      setTextY(existingText.normalizedY);
      setTextStart(existingText.startTimeMs);
      setTextEnd(existingText.endTimeMs);
    } else {
      setTextStart(playheadMs);
      setTextEnd(Math.min(totalDurationMs, Math.max(playheadMs + 3000, 3000)));
    }
  }, [existingText?.id, activeTool]);

  // Voiceover recorder state
  const [recordingVo, setRecordingVo] = useState(false);
  const [voError, setVoError] = useState<string | null>(null);
  const voRecorderRef = useRef<MediaRecorder | null>(null);
  const voStartRef = useRef<number>(0);

  if (activeTool === 'none') return null;

  const startVoiceoverRecord = async () => {
    setVoError(null);
    if (!navigator.mediaDevices?.getUserMedia) {
      setVoError('Microphone API unavailable in this browser.');
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const chunks: BlobPart[] = [];
      const mr = new MediaRecorder(stream);
      mr.ondataavailable = (e) => {
        if (e.data.size > 0) chunks.push(e.data);
      };
      mr.onstop = () => {
        stream.getTracks().forEach((t) => t.stop());
        const blob = new Blob(chunks, { type: 'audio/webm' });
        const url = URL.createObjectURL(blob);
        const durMs = Math.max(800, Date.now() - voStartRef.current);
        onSetVoiceover({
          id: `vo_${Date.now()}`,
          title: 'Voiceover',
          sourceUrl: url,
          sourceDurationMs: durMs,
          trimStartMs: 0,
          trimEndMs: durMs,
          timelineStartMs: playheadMs,
          volume: 1,
          isMuted: false,
        });
      };
      voRecorderRef.current = mr;
      voStartRef.current = Date.now();
      mr.start(200);
      setRecordingVo(true);
    } catch {
      setVoError('Microphone permission denied or unavailable.');
    }
  };

  const stopVoiceoverRecord = () => {
    voRecorderRef.current?.stop();
    setRecordingVo(false);
  };

  const selectedClipIdx = selectedClip
    ? timeline.clips.findIndex((c) => c.id === selectedClip.id)
    : -1;

  return (
    <section
      aria-label={`${activeTool} controls`}
      className="mx-3 mt-2 rounded-[16px] border border-[#2B352D] bg-[#18110D] p-3.5"
    >
      {activeTool === 'trim' && selectedClip && (
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-bold text-white">Trim Clip · {selectedClip.displayName}</h2>
            <span className="text-xs text-[#FF8232] font-mono-tabular">
              {formatDurationBadge(selectedClip.startPositionMs)} –{' '}
              {formatDurationBadge(selectedClip.endPositionMs)}
            </span>
          </div>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <label className="block text-xs text-[#9AA69B]">
              Start ({formatDurationBadge(selectedClip.startPositionMs)})
              <input
                type="range"
                min={0}
                max={Math.max(0, selectedClip.endPositionMs - 400)}
                step={100}
                value={selectedClip.startPositionMs}
                onChange={(e) =>
                  onTrimClip(selectedClip.id, Number(e.target.value), selectedClip.endPositionMs)
                }
                className="mt-1 w-full accent-[#FF5500]"
              />
            </label>
            <label className="block text-xs text-[#9AA69B]">
              End ({formatDurationBadge(selectedClip.endPositionMs)})
              <input
                type="range"
                min={Math.min(selectedClip.sourceDurationMs, selectedClip.startPositionMs + 400)}
                max={selectedClip.sourceDurationMs}
                step={100}
                value={selectedClip.endPositionMs}
                onChange={(e) =>
                  onTrimClip(selectedClip.id, selectedClip.startPositionMs, Number(e.target.value))
                }
                className="mt-1 w-full accent-[#FF5500]"
              />
            </label>
          </div>
        </div>
      )}

      {activeTool === 'split' && (
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 className="text-sm font-bold text-white">Split at Playhead</h2>
            <p className="text-xs text-[#9AA69B] font-mono-tabular">
              Current position: {formatDurationBadge(playheadMs)}
            </p>
          </div>
          <button
            type="button"
            onClick={() => {
              onSplitAtPlayhead();
              onClose();
            }}
            className="inline-flex min-h-[42px] items-center gap-2 rounded-xl bg-[#FF5500] px-4 py-2 text-xs font-bold text-white hover:bg-[#FF8232]"
          >
            <Scissors className="h-4 w-4" />
            <span>Split Clip</span>
          </button>
        </div>
      )}

      {activeTool === 'crop' && selectedClip && (
        <div className="space-y-3">
          <h2 className="text-sm font-bold text-white">Crop & Aspect Ratio</h2>
          <div className="grid grid-cols-3 gap-2">
            {(Object.keys(ASPECT_PRESETS) as AspectRatioPreset[]).map((key) => {
              const preset = ASPECT_PRESETS[key];
              const active = timeline.aspectRatio === key;
              return (
                <button
                  key={key}
                  type="button"
                  onClick={() =>
                    onChangeAspectAndCrop(
                      key,
                      selectedClip.cropConfig.zoom,
                      selectedClip.cropConfig.offsetX,
                      selectedClip.cropConfig.offsetY
                    )
                  }
                  className={`min-h-[40px] rounded-xl border px-3 py-2 text-xs font-semibold transition-colors ${
                    active
                      ? 'border-[#FF8232] bg-[#FF5500] text-white'
                      : 'border-[#2B352D] bg-[#221711] text-[#9AA69B] hover:text-white'
                  }`}
                >
                  {preset.label}
                </button>
              );
            })}
          </div>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <label className="block text-xs text-[#9AA69B]">
              Zoom ({selectedClip.cropConfig.zoom.toFixed(1)}x)
              <input
                type="range"
                min={1}
                max={2.4}
                step={0.1}
                value={selectedClip.cropConfig.zoom}
                onChange={(e) =>
                  onChangeAspectAndCrop(
                    timeline.aspectRatio,
                    Number(e.target.value),
                    selectedClip.cropConfig.offsetX,
                    selectedClip.cropConfig.offsetY
                  )
                }
                className="mt-1 w-full accent-[#FF5500]"
              />
            </label>
            <label className="block text-xs text-[#9AA69B]">
              Pan X
              <input
                type="range"
                min={-1}
                max={1}
                step={0.1}
                value={selectedClip.cropConfig.offsetX}
                onChange={(e) =>
                  onChangeAspectAndCrop(
                    timeline.aspectRatio,
                    selectedClip.cropConfig.zoom,
                    Number(e.target.value),
                    selectedClip.cropConfig.offsetY
                  )
                }
                className="mt-1 w-full accent-[#FF5500]"
              />
            </label>
            <label className="block text-xs text-[#9AA69B]">
              Pan Y
              <input
                type="range"
                min={-1}
                max={1}
                step={0.1}
                value={selectedClip.cropConfig.offsetY}
                onChange={(e) =>
                  onChangeAspectAndCrop(
                    timeline.aspectRatio,
                    selectedClip.cropConfig.zoom,
                    selectedClip.cropConfig.offsetX,
                    Number(e.target.value)
                  )
                }
                className="mt-1 w-full accent-[#FF5500]"
              />
            </label>
          </div>
        </div>
      )}

      {activeTool === 'rotate' && selectedClip && (
        <div className="flex items-center justify-between gap-3">
          <div>
            <h2 className="text-sm font-bold text-white">Rotate Video</h2>
            <p className="text-xs text-[#9AA69B] font-mono-tabular">
              Current angle: {selectedClip.rotationDegrees}°
            </p>
          </div>
          <button
            type="button"
            onClick={() => onRotateClip(selectedClip.id)}
            className="inline-flex min-h-[42px] items-center gap-2 rounded-xl bg-[#FF5500] px-4 py-2 text-xs font-bold text-white hover:bg-[#FF8232]"
          >
            <RotateCw className="h-4 w-4" />
            <span>Rotate +90°</span>
          </button>
        </div>
      )}

      {activeTool === 'speed' && selectedClip && (
        <div className="space-y-2.5">
          <h2 className="text-sm font-bold text-white">Playback Speed</h2>
          <div className="grid grid-cols-5 gap-2">
            {[0.5, 0.75, 1, 1.5, 2].map((spd) => {
              const active = Math.abs(selectedClip.playbackSpeed - spd) < 0.01;
              return (
                <button
                  key={spd}
                  type="button"
                  onClick={() => onChangeSpeed(selectedClip.id, spd)}
                  className={`min-h-[40px] rounded-xl border text-xs font-bold font-mono-tabular ${
                    active
                      ? 'border-[#FF8232] bg-[#FF5500] text-white'
                      : 'border-[#2B352D] bg-[#221711] text-[#9AA69B] hover:text-white'
                  }`}
                >
                  {spd}x
                </button>
              );
            })}
          </div>
        </div>
      )}

      {activeTool === 'volume' && selectedClip && (
        <div className="space-y-2.5">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-bold text-white font-mono-tabular">
              Clip Volume: {selectedClip.isMuted ? 0 : Math.round(selectedClip.volume * 100)}%
            </h2>
            <button
              type="button"
              onClick={() =>
                onChangeVolume(selectedClip.id, selectedClip.volume, !selectedClip.isMuted)
              }
              className="inline-flex min-h-[38px] items-center gap-1.5 rounded-xl border border-[#2B352D] bg-[#221711] px-3 text-xs font-semibold text-[#FF8232]"
            >
              {selectedClip.isMuted ? (
                <>
                  <VolumeX className="h-4 w-4" />
                  <span>Unmute</span>
                </>
              ) : (
                <>
                  <Volume2 className="h-4 w-4" />
                  <span>Mute</span>
                </>
              )}
            </button>
          </div>
          <input
            type="range"
            min={0}
            max={2}
            step={0.05}
            value={selectedClip.isMuted ? 0 : selectedClip.volume}
            onChange={(e) => {
              const v = Number(e.target.value);
              onChangeVolume(selectedClip.id, v, v <= 0.01);
            }}
            className="w-full accent-[#FF5500]"
          />
        </div>
      )}

      {activeTool === 'music' && (
        <div className="space-y-3">
          <div className="flex items-center justify-between gap-2">
            <div>
              <h2 className="text-sm font-bold text-white">Music Library</h2>
              <p className="text-[11px] text-[#9AA69B]">
                Loaded from <code className="text-white">assets/music/</code> or device audio
              </p>
            </div>
            <button
              type="button"
              onClick={() => audioInputRef.current?.click()}
              className="inline-flex min-h-[38px] items-center gap-1.5 rounded-xl border border-[#FF5500] bg-[#221711] px-3 text-xs font-semibold text-[#FF8232] hover:bg-[#FF5500] hover:text-white transition-colors"
            >
              <Upload className="h-3.5 w-3.5" />
              <span>Import Audio</span>
            </button>
          </div>

          <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
            {bundledMusic.map((track) => {
              const active = timeline.musicTrack?.title === track.title;
              return (
                <button
                  key={track.id}
                  type="button"
                  onClick={() => {
                    const audio = new Audio(track.url);
                    audio.onloadedmetadata = () => {
                      const durMs = Math.max(3000, Math.round((audio.duration || 18) * 1000));
                      onSelectMusic({
                        id: track.id,
                        title: track.title,
                        sourceUrl: track.url,
                        isBundledAsset: true,
                        sourceDurationMs: durMs,
                        trimStartMs: 0,
                        trimEndMs: durMs,
                        timelineStartMs: 0,
                        volume: 0.75,
                        isMuted: false,
                        loopToFitVideo: true,
                      });
                    };
                  }}
                  className={`flex min-h-[44px] items-center justify-between rounded-xl border px-3 py-2 text-left text-xs ${
                    active
                      ? 'border-[#FF5500] bg-[#221711] text-white'
                      : 'border-[#2B352D] bg-[#0B0807] text-[#9AA69B] hover:text-white'
                  }`}
                >
                  <span className="flex items-center gap-2 truncate font-medium text-white">
                    <Music className="h-3.5 w-3.5 shrink-0 text-[#FF5500]" />
                    <span className="truncate">{track.title}</span>
                  </span>
                </button>
              );
            })}
          </div>

          {timeline.musicTrack && (
            <div className="rounded-xl border border-[#2B352D] bg-[#0B0807] p-3 space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold text-white">
                  {timeline.musicTrack.title} · Volume {Math.round(timeline.musicTrack.volume * 100)}%
                </span>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() =>
                      onUpdateMusic({ isMuted: !timeline.musicTrack?.isMuted })
                    }
                    className="text-xs font-medium text-[#9AA69B] hover:text-white"
                  >
                    {timeline.musicTrack.isMuted ? 'Unmute' : 'Mute'}
                  </button>
                  <button
                    type="button"
                    onClick={onRemoveMusic}
                    aria-label="Remove music track"
                    className="text-[#FF5A5A] hover:opacity-80"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </div>
              </div>
              <input
                type="range"
                min={0}
                max={1}
                step={0.05}
                value={timeline.musicTrack.volume}
                onChange={(e) => onUpdateMusic({ volume: Number(e.target.value), isMuted: false })}
                className="w-full accent-[#FF5500]"
              />
            </div>
          )}

          <input
            ref={audioInputRef}
            type="file"
            accept="audio/*"
            className="hidden"
            onChange={(e) => {
              const file = e.target.files?.[0];
              if (!file) return;
              const url = URL.createObjectURL(file);
              const audio = new Audio(url);
              audio.onloadedmetadata = () => {
                const durMs = Math.max(2000, Math.round((audio.duration || 12) * 1000));
                onSelectMusic({
                  id: `music_${Date.now()}`,
                  title: file.name.replace(/\.[^.]+$/, ''),
                  sourceUrl: url,
                  isBundledAsset: false,
                  sourceDurationMs: durMs,
                  trimStartMs: 0,
                  trimEndMs: durMs,
                  timelineStartMs: 0,
                  volume: 0.75,
                  isMuted: false,
                  loopToFitVideo: true,
                });
              };
              e.target.value = '';
            }}
          />
        </div>
      )}

      {activeTool === 'text' && (
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-bold text-white">Text Overlay</h2>
            {existingText && (
              <button
                type="button"
                onClick={() => onDeleteText(existingText.id)}
                className="inline-flex items-center gap-1 text-xs font-semibold text-[#FF5A5A]"
              >
                <Trash2 className="h-3.5 w-3.5" />
                Remove
              </button>
            )}
          </div>

          <input
            type="text"
            value={textInput}
            onChange={(e) => setTextInput(e.target.value)}
            placeholder="Enter title or caption..."
            className="min-h-[42px] w-full rounded-xl border border-[#2B352D] bg-[#0B0807] px-3 py-2 text-sm text-white focus:border-[#FF5500] focus:outline-none"
          />

          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              {['#FFFFFF', '#FF5500', '#FF8232', '#82B8FF', '#FF5A5A'].map((hex) => (
                <button
                  key={hex}
                  type="button"
                  aria-label={`Text color ${hex}`}
                  onClick={() => setTextColor(hex)}
                  style={{ backgroundColor: hex }}
                  className={`h-7 w-7 rounded-full border-2 ${
                    textColor === hex ? 'border-[#FF5500] scale-110' : 'border-[#2B352D]'
                  }`}
                />
              ))}
            </div>

            <div className="flex items-center gap-1 rounded-lg border border-[#2B352D] bg-[#0B0807] p-1">
              {(['left', 'center', 'right'] as TextAlignOption[]).map((al) => (
                <button
                  key={al}
                  type="button"
                  onClick={() => setTextAlign(al)}
                  className={`rounded-md px-2.5 py-1 text-xs font-medium capitalize ${
                    textAlign === al ? 'bg-[#FF5500] text-white' : 'text-[#9AA69B]'
                  }`}
                >
                  {al}
                </button>
              ))}
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <label className="block text-xs text-[#9AA69B]">
              Size ({textSize}px)
              <input
                type="range"
                min={16}
                max={48}
                value={textSize}
                onChange={(e) => setTextSize(Number(e.target.value))}
                className="mt-1 w-full accent-[#FF5500]"
              />
            </label>
            <label className="block text-xs text-[#9AA69B]">
              Vertical Pos ({Math.round(textY * 100)}%)
              <input
                type="range"
                min={0.12}
                max={0.88}
                step={0.02}
                value={textY}
                onChange={(e) => setTextY(Number(e.target.value))}
                className="mt-1 w-full accent-[#FF5500]"
              />
            </label>
          </div>

          <button
            type="button"
            onClick={() => {
              if (!textInput.trim()) return;
              onUpsertText({
                id: existingText?.id || `txt_${Date.now()}`,
                text: textInput.trim(),
                fontSizePx: textSize,
                colorHex: textColor,
                alignment: textAlign,
                normalizedX: textAlign === 'left' ? 0.15 : textAlign === 'right' ? 0.85 : 0.5,
                normalizedY: textY,
                startTimeMs: Math.min(textStart, totalDurationMs - 500),
                endTimeMs: Math.max(textStart + 500, Math.min(textEnd, totalDurationMs)),
              });
              onClose();
            }}
            className="min-h-[42px] w-full rounded-xl bg-[#FF5500] text-xs font-bold text-white hover:bg-[#FF8232]"
          >
            {existingText ? 'Update Text' : 'Add Text Overlay'}
          </button>
        </div>
      )}

      {activeTool === 'voice' && (
        <div className="space-y-3">
          <h2 className="text-sm font-bold text-white">Voiceover</h2>
          {voError && <p className="text-xs text-[#FF5A5A]">{voError}</p>}

          <button
            type="button"
            onClick={recordingVo ? stopVoiceoverRecord : startVoiceoverRecord}
            className={`flex min-h-[44px] w-full items-center justify-center gap-2 rounded-xl text-xs font-bold text-white ${
              recordingVo ? 'bg-[#FF5A5A]' : 'bg-[#FF5500] hover:bg-[#FF8232]'
            }`}
          >
            {recordingVo ? <Square className="h-4 w-4" /> : <Mic className="h-4 w-4" />}
            <span>{recordingVo ? 'Stop Voiceover Recording' : 'Record Voiceover'}</span>
          </button>

          {timeline.voiceovers[0] && (
            <div className="flex items-center justify-between rounded-xl border border-[#2B352D] bg-[#0B0807] p-3">
              <div className="flex-1 pr-3">
                <p className="text-xs font-semibold text-white">
                  Recorded Voiceover ({formatDurationBadge(timeline.voiceovers[0].sourceDurationMs)})
                </p>
                <input
                  type="range"
                  min={0}
                  max={1}
                  step={0.05}
                  value={timeline.voiceovers[0].volume}
                  onChange={(e) =>
                    onSetVoiceover({
                      ...timeline.voiceovers[0],
                      volume: Number(e.target.value),
                    })
                  }
                  className="mt-1.5 w-full accent-[#FF5500]"
                />
              </div>
              <button
                type="button"
                onClick={onDeleteVoiceover}
                aria-label="Delete voiceover"
                className="flex h-10 w-10 items-center justify-center rounded-lg text-[#FF5A5A] hover:bg-[#18110D]"
              >
                <Trash2 className="h-4 w-4" />
              </button>
            </div>
          )}
        </div>
      )}

      {activeTool === 'clips' && selectedClip && (
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-bold text-white">
              Clip {selectedClipIdx + 1} of {timeline.clips.length} · {selectedClip.displayName}
            </h2>
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              disabled={selectedClipIdx <= 0}
              onClick={() => onReorderClip(selectedClipIdx, selectedClipIdx - 1)}
              className="flex min-h-[42px] flex-1 items-center justify-center gap-1.5 rounded-xl border border-[#2B352D] bg-[#221711] text-xs font-semibold text-white disabled:opacity-40"
            >
              <ArrowLeft className="h-4 w-4" />
              <span>Move Left</span>
            </button>
            <button
              type="button"
              disabled={selectedClipIdx < 0 || selectedClipIdx >= timeline.clips.length - 1}
              onClick={() => onReorderClip(selectedClipIdx, selectedClipIdx + 1)}
              className="flex min-h-[42px] flex-1 items-center justify-center gap-1.5 rounded-xl border border-[#2B352D] bg-[#221711] text-xs font-semibold text-white disabled:opacity-40"
            >
              <span>Move Right</span>
              <ArrowRight className="h-4 w-4" />
            </button>
            <button
              type="button"
              disabled={timeline.clips.length <= 1}
              onClick={() => onDeleteClip(selectedClip.id)}
              aria-label="Delete selected clip"
              className="flex min-h-[42px] items-center justify-center rounded-xl border border-[#FF5A5A]/50 bg-[#221711] px-4 text-[#FF5A5A] disabled:opacity-40"
            >
              <Trash2 className="h-4 w-4" />
            </button>
          </div>
        </div>
      )}
    </section>
  );
}

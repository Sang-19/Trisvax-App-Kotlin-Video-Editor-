import React, { useRef } from 'react';
import { Mic, Music, Plus, Type, Volume2, VolumeX } from 'lucide-react';
import { getClipEffectiveDurationMs, getTimelineTotalDurationMs } from '../engine/timelineMath';
import type { EditorToolId, TimelineState } from '../types/editor';

const PX_PER_SECOND = 48;

export function TimelineTracks({
  timeline,
  selectedClipId,
  playheadMs,
  onSeek,
  onSelectClip,
  onToggleClipMute,
  onSelectTool,
  onSelectTextOverlay,
}: {
  timeline: TimelineState;
  selectedClipId: string | null;
  playheadMs: number;
  onSeek: (ms: number) => void;
  onSelectClip: (clipId: string) => void;
  onToggleClipMute: (clipId: string) => void;
  onSelectTool: (tool: EditorToolId) => void;
  onSelectTextOverlay: (id: string) => void;
}) {
  const trackContainerRef = useRef<HTMLDivElement | null>(null);
  const draggingRef = useRef(false);

  const totalDurationMs = Math.max(3000, getTimelineTotalDurationMs(timeline));
  const trackWidthPx = Math.max(320, (totalDurationMs / 1000) * PX_PER_SECOND + 64);
  const playheadLeftPx = Math.max(0, (playheadMs / 1000) * PX_PER_SECOND);

  const seekFromClientX = (clientX: number) => {
    const el = trackContainerRef.current;
    if (!el) return;
    const rect = el.getBoundingClientRect();
    const x = Math.max(0, clientX - rect.left);
    const ms = Math.min(totalDurationMs, Math.round((x / PX_PER_SECOND) * 1000));
    onSeek(ms);
  };

  return (
    <div
      data-testid="timeline-tracks"
      className="mx-3 rounded-[16px] border border-[#2B352D] bg-[#18110D] p-3"
    >
      <div className="overflow-x-auto pb-1">
        <div
          ref={trackContainerRef}
          style={{ width: `${trackWidthPx}px` }}
          onPointerDown={(e) => {
            if ((e.target as HTMLElement).closest('button')) return;
            draggingRef.current = true;
            seekFromClientX(e.clientX);
          }}
          onPointerMove={(e) => {
            if (draggingRef.current) {
              seekFromClientX(e.clientX);
            }
          }}
          onPointerUp={() => {
            draggingRef.current = false;
          }}
          onPointerLeave={() => {
            draggingRef.current = false;
          }}
          className="relative flex flex-col gap-2 select-none"
        >
          {/* Track 1: Main video clips strip */}
          <div className="flex h-[54px] items-stretch gap-1">
            {timeline.clips.map((clip) => {
              const effMs = getClipEffectiveDurationMs(clip);
              const clipWidthPx = Math.max(54, (effMs / 1000) * PX_PER_SECOND);
              const selected = clip.id === selectedClipId;
              const tileCount = Math.max(1, Math.min(10, Math.round(clipWidthPx / 42)));

              return (
                <div
                  key={clip.id}
                  style={{ width: `${clipWidthPx}px` }}
                  className={`relative shrink-0 overflow-hidden rounded-[8px] border bg-[#221711] transition-colors ${
                    selected ? 'border-2 border-[#FF5500]' : 'border-[#2B352D]'
                  }`}
                >
                  <button
                    type="button"
                    onClick={() => onSelectClip(clip.id)}
                    aria-label={`Select clip ${clip.displayName}`}
                    className="flex h-full w-full items-stretch overflow-hidden"
                  >
                    {Array.from({ length: tileCount }).map((_, idx) => {
                      const thumb =
                        clip.thumbnails[idx % Math.max(1, clip.thumbnails.length)] || '';
                      return thumb ? (
                        <img
                          key={idx}
                          src={thumb}
                          alt=""
                          referrerPolicy="no-referrer"
                          className="h-full flex-1 object-cover"
                        />
                      ) : (
                        <div
                          key={idx}
                          className="h-full flex-1 bg-gradient-to-br from-[#221711] to-[#0B0807]"
                        />
                      );
                    })}
                  </button>

                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      onToggleClipMute(clip.id);
                    }}
                    aria-label={clip.isMuted ? `Unmute ${clip.displayName}` : `Mute ${clip.displayName}`}
                    className="absolute left-1.5 top-1/2 flex h-7 w-7 -translate-y-1/2 items-center justify-center rounded-full bg-black/70 text-white hover:bg-black"
                  >
                    {clip.isMuted || clip.volume <= 0.01 ? (
                      <VolumeX className="h-3.5 w-3.5 text-[#FF5A5A]" />
                    ) : (
                      <Volume2 className="h-3.5 w-3.5" />
                    )}
                  </button>
                </div>
              );
            })}
          </div>

          {/* Track 2: Music / Audio */}
          <div className="flex h-[34px] items-center">
            {timeline.musicTrack ? (
              <button
                type="button"
                onClick={() => onSelectTool('music')}
                style={{
                  marginLeft: `${(timeline.musicTrack.timelineStartMs / 1000) * PX_PER_SECOND}px`,
                  width: `${Math.max(
                    110,
                    ((timeline.musicTrack.trimEndMs - timeline.musicTrack.trimStartMs) / 1000) *
                      PX_PER_SECOND
                  )}px`,
                }}
                className="flex h-full items-center gap-2 rounded-[8px] border border-[#FF5500] bg-[#221711] px-2.5 text-left text-xs font-medium text-white hover:border-[#FF8232]"
              >
                <Music className="h-3.5 w-3.5 shrink-0 text-[#FF5500]" />
                <span className="truncate">{timeline.musicTrack.title}</span>
              </button>
            ) : (
              <button
                type="button"
                onClick={() => onSelectTool('music')}
                className="inline-flex h-[32px] items-center gap-1.5 rounded-[8px] border border-[#2B352D] bg-[#221711] px-3 text-xs font-medium text-white hover:border-[#FF5500]"
              >
                <Plus className="h-3.5 w-3.5" />
                <span>Add audio</span>
              </button>
            )}
          </div>

          {/* Track 3: Text overlays */}
          <div className="relative flex h-[34px] items-center gap-1.5">
            {timeline.textOverlays.length === 0 ? (
              <button
                type="button"
                onClick={() => onSelectTool('text')}
                className="inline-flex h-[32px] items-center gap-1.5 rounded-[8px] border border-[#2B352D] bg-[#221711] px-3 text-xs font-medium text-white hover:border-[#82B8FF]"
              >
                <Plus className="h-3.5 w-3.5" />
                <span>Add text</span>
              </button>
            ) : (
              <>
                {timeline.textOverlays.map((ov) => {
                  const widthPx = Math.max(
                    90,
                    ((ov.endTimeMs - ov.startTimeMs) / 1000) * PX_PER_SECOND
                  );
                  return (
                    <button
                      key={ov.id}
                      type="button"
                      onClick={() => {
                        onSelectTextOverlay(ov.id);
                        onSelectTool('text');
                      }}
                      style={{ width: `${widthPx}px` }}
                      className="flex h-[32px] shrink-0 items-center gap-1.5 rounded-[8px] border border-[#82B8FF] bg-[#221711] px-2.5 text-left text-xs font-medium text-white"
                    >
                      <Type className="h-3.5 w-3.5 shrink-0 text-[#82B8FF]" />
                      <span className="truncate">{ov.text}</span>
                    </button>
                  );
                })}
                <button
                  type="button"
                  onClick={() => {
                    onSelectTextOverlay('');
                    onSelectTool('text');
                  }}
                  className="inline-flex h-[32px] shrink-0 items-center gap-1 rounded-[8px] border border-[#2B352D] bg-[#221711] px-2.5 text-xs font-medium text-white hover:border-[#82B8FF]"
                >
                  <Plus className="h-3.5 w-3.5" />
                  <span>Add text</span>
                </button>
              </>
            )}
          </div>

          {/* Track 4: Voiceover */}
          <div className="flex h-[34px] items-center">
            {timeline.voiceovers.length > 0 ? (
              <button
                type="button"
                onClick={() => onSelectTool('voice')}
                style={{
                  marginLeft: `${(timeline.voiceovers[0].timelineStartMs / 1000) * PX_PER_SECOND}px`,
                  width: `${Math.max(
                    110,
                    ((timeline.voiceovers[0].trimEndMs - timeline.voiceovers[0].trimStartMs) /
                      1000) *
                      PX_PER_SECOND
                  )}px`,
                }}
                className="flex h-full items-center gap-2 rounded-[8px] border border-[#FF8232] bg-[#221711] px-2.5 text-left text-xs font-medium text-white"
              >
                <Mic className="h-3.5 w-3.5 shrink-0 text-[#FF8232]" />
                <span className="truncate">{timeline.voiceovers[0].title}</span>
              </button>
            ) : (
              <button
                type="button"
                onClick={() => onSelectTool('voice')}
                className="inline-flex h-[32px] items-center gap-1.5 rounded-[8px] border border-[#2B352D] bg-[#221711] px-3 text-xs font-medium text-white hover:border-[#FF5500]"
              >
                <Plus className="h-3.5 w-3.5" />
                <span>Add voiceover</span>
              </button>
            )}
          </div>

          {/* Vertical Playhead line + circular center dot */}
          <div
            style={{ left: `${playheadLeftPx}px` }}
            onPointerDown={(e) => {
              e.stopPropagation();
              draggingRef.current = true;
            }}
            className="pointer-events-auto absolute bottom-0 top-0 z-10 flex w-4 -translate-x-1/2 cursor-ew-resize items-center justify-center"
          >
            <div className="h-full w-[2px] bg-white shadow" />
            <div className="absolute h-3 w-3 rounded-full border-2 border-[#0B0807] bg-white" />
          </div>
        </div>
      </div>
    </div>
  );
}

import { ASPECT_PRESETS, type TimelineState, type VideoClipItem } from '../types/editor';
import { findClipAtPlayhead, getTimelineTotalDurationMs } from './timelineMath';

export async function extractVideoMetadata(
  url: string
): Promise<{ durationMs: number; width: number; height: number }> {
  return new Promise((resolve) => {
    const video = document.createElement('video');
    video.preload = 'metadata';
    video.muted = true;
    video.playsInline = true;
    video.crossOrigin = 'anonymous';
    video.src = url;

    const cleanup = () => {
      video.removeAttribute('src');
      video.load();
    };

    const timer = window.setTimeout(() => {
      cleanup();
      resolve({ durationMs: 5000, width: 1080, height: 1920 });
    }, 3500);

    video.onloadedmetadata = () => {
      window.clearTimeout(timer);
      const durationMs = Math.max(1000, Math.round((video.duration || 5) * 1000));
      const width = video.videoWidth || 1080;
      const height = video.videoHeight || 1920;
      cleanup();
      resolve({ durationMs, width, height });
    };

    video.onerror = () => {
      window.clearTimeout(timer);
      cleanup();
      resolve({ durationMs: 5000, width: 1080, height: 1920 });
    };
  });
}

export async function extractVideoThumbnails(
  url: string,
  durationMs: number,
  count: number = 4
): Promise<string[]> {
  return new Promise((resolve) => {
    const video = document.createElement('video');
    video.muted = true;
    video.playsInline = true;
    video.crossOrigin = 'anonymous';
    video.preload = 'auto';
    video.src = url;

    const thumbs: string[] = [];
    const canvas = document.createElement('canvas');
    canvas.width = 160;
    canvas.height = 120;
    const ctx = canvas.getContext('2d');

    const failSafe = window.setTimeout(() => {
      resolve(thumbs);
    }, 5000);

    const captureAtIndex = (idx: number) => {
      if (idx >= count) {
        window.clearTimeout(failSafe);
        video.removeAttribute('src');
        video.load();
        resolve(thumbs);
        return;
      }
      const targetSec = ((idx + 0.5) / count) * Math.max(0.8, durationMs / 1000);
      video.currentTime = Math.min(Math.max(0.05, targetSec), Math.max(0.1, (video.duration || durationMs / 1000) - 0.05));
    };

    video.onloadeddata = () => {
      captureAtIndex(0);
    };

    video.onseeked = () => {
      if (ctx) {
        ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
        try {
          thumbs.push(canvas.toDataURL('image/jpeg', 0.72));
        } catch {
          // ignore tainted canvas fallback
        }
      }
      captureAtIndex(thumbs.length);
    };

    video.onerror = () => {
      window.clearTimeout(failSafe);
      resolve(thumbs);
    };
  });
}

export async function extractImageThumbnail(url: string): Promise<string> {
  return new Promise((resolve) => {
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => {
      const canvas = document.createElement('canvas');
      canvas.width = 180;
      canvas.height = 180;
      const ctx = canvas.getContext('2d');
      if (ctx) {
        ctx.drawImage(img, 0, 0, 180, 180);
        resolve(canvas.toDataURL('image/jpeg', 0.8));
      } else {
        resolve(url);
      }
    };
    img.onerror = () => resolve(url);
    img.src = url;
  });
}

/**
 * Renders the edited timeline (clips, trim, split, crop, rotation, speed,
 * text overlays, and audio) into a real video stream and transcodes via
 * /api/export-mp4 (FFmpeg) to a genuine 1080p H.264/AAC MP4 file.
 */
export async function exportTimelineTo1080pMp4(
  timeline: TimelineState,
  onProgress: (percent: number, message: string) => void,
  signal: AbortSignal
): Promise<{ blobUrl: string; fileName: string }> {
  const totalMs = Math.max(500, getTimelineTotalDurationMs(timeline));
  const preset = ASPECT_PRESETS[timeline.aspectRatio];
  // Render offscreen canvas at clean 720p/540p scaled aspect then FFmpeg upscales cleanly to exact 1080p H.264 MP4
  const renderW = Math.round(preset.exportWidth / 2);
  const renderH = Math.round(preset.exportHeight / 2);

  const canvas = document.createElement('canvas');
  canvas.width = renderW;
  canvas.height = renderH;
  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('Canvas 2D context unavailable');

  onProgress(6, 'Loading media assets...');

  // Preload video/image elements per unique clip sourceUrl
  const mediaCache = new Map<string, HTMLVideoElement | HTMLImageElement>();
  for (const clip of timeline.clips) {
    if (mediaCache.has(clip.sourceUrl)) continue;
    if (clip.isImage) {
      const img = new Image();
      img.crossOrigin = 'anonymous';
      img.src = clip.sourceUrl;
      await new Promise<void>((res) => {
        img.onload = () => res();
        img.onerror = () => res();
      });
      mediaCache.set(clip.sourceUrl, img);
    } else {
      const vid = document.createElement('video');
      vid.crossOrigin = 'anonymous';
      vid.muted = true;
      vid.playsInline = true;
      vid.preload = 'auto';
      vid.src = clip.sourceUrl;
      await new Promise<void>((res) => {
        vid.onloadeddata = () => res();
        vid.onerror = () => res();
        window.setTimeout(res, 3000);
      });
      mediaCache.set(clip.sourceUrl, vid);
    }
  }

  if (signal.aborted) throw new Error('Export cancelled');

  // Setup WebAudio mixing for music + voiceover
  const audioCtx = new AudioContext();
  const dest = audioCtx.createMediaStreamDestination();

  // Add subtle silent oscillator so audio stream always has valid samples
  const silentOsc = audioCtx.createOscillator();
  const silentGain = audioCtx.createGain();
  silentGain.gain.value = 0.0001;
  silentOsc.connect(silentGain).connect(dest);
  silentOsc.start();

  const audioElements: HTMLAudioElement[] = [];
  if (timeline.musicTrack && !timeline.musicTrack.isMuted) {
    const musicEl = new Audio(timeline.musicTrack.sourceUrl);
    musicEl.crossOrigin = 'anonymous';
    musicEl.loop = timeline.musicTrack.loopToFitVideo;
    musicEl.currentTime = timeline.musicTrack.trimStartMs / 1000;
    const srcNode = audioCtx.createMediaElementSource(musicEl);
    const gainNode = audioCtx.createGain();
    gainNode.gain.value = timeline.musicTrack.volume;
    srcNode.connect(gainNode).connect(dest);
    audioElements.push(musicEl);
  }

  if (timeline.voiceovers[0] && !timeline.voiceovers[0].isMuted) {
    const voEl = new Audio(timeline.voiceovers[0].sourceUrl);
    voEl.crossOrigin = 'anonymous';
    voEl.currentTime = timeline.voiceovers[0].trimStartMs / 1000;
    const srcNode = audioCtx.createMediaElementSource(voEl);
    const gainNode = audioCtx.createGain();
    gainNode.gain.value = timeline.voiceovers[0].volume;
    srcNode.connect(gainNode).connect(dest);
    audioElements.push(voEl);
  }

  const stream = canvas.captureStream(30);
  const combinedStream = new MediaStream([
    ...stream.getVideoTracks(),
    ...dest.stream.getAudioTracks(),
  ]);

  const mimeType = MediaRecorder.isTypeSupported('video/webm;codecs=vp8,opus')
    ? 'video/webm;codecs=vp8,opus'
    : 'video/webm';

  const recorder = new MediaRecorder(combinedStream, {
    mimeType,
    videoBitsPerSecond: 4_000_000,
  });

  const chunks: BlobPart[] = [];
  recorder.ondataavailable = (e) => {
    if (e.data && e.data.size > 0) chunks.push(e.data);
  };

  const recordingDone = new Promise<Blob>((resolve, reject) => {
    recorder.onstop = () => resolve(new Blob(chunks, { type: mimeType }));
    recorder.onerror = () => reject(new Error('MediaRecorder error during export'));
  });

  recorder.start(250);
  for (const el of audioElements) {
    el.play().catch(() => {});
  }

  const startWall = performance.now();
  // Step frames in real time (capped nicely at timeline length)
  await new Promise<void>((resolve, reject) => {
    const tick = async () => {
      if (signal.aborted) {
        recorder.stop();
        reject(new Error('Export cancelled'));
        return;
      }

      const elapsedMs = performance.now() - startWall;
      const pct = Math.min(72, Math.round(10 + (elapsedMs / totalMs) * 62));
      onProgress(pct, `Rendering timeline (${Math.min(100, Math.round((elapsedMs / totalMs) * 100))}%)...`);

      const lookup = findClipAtPlayhead(timeline.clips, elapsedMs);
      ctx.fillStyle = '#0B0807';
      ctx.fillRect(0, 0, renderW, renderH);

      if (lookup) {
        const { clip, localSourceTimeMs } = lookup;
        const sourceEl = mediaCache.get(clip.sourceUrl);
        if (sourceEl instanceof HTMLVideoElement) {
          const targetSec = localSourceTimeMs / 1000;
          if (Math.abs(sourceEl.currentTime - targetSec) > 0.08) {
            sourceEl.currentTime = targetSec;
          }
          drawTransformedMedia(ctx, sourceEl, sourceEl.videoWidth || renderW, sourceEl.videoHeight || renderH, renderW, renderH, clip);
        } else if (sourceEl instanceof HTMLImageElement) {
          drawTransformedMedia(ctx, sourceEl, sourceEl.naturalWidth || renderW, sourceEl.naturalHeight || renderH, renderW, renderH, clip);
        }
      }

      // Burn active text overlays into exported frames
      for (const overlay of timeline.textOverlays) {
        if (elapsedMs >= overlay.startTimeMs && elapsedMs <= overlay.endTimeMs) {
          ctx.save();
          ctx.font = `700 ${Math.round(overlay.fontSizePx * (renderW / 360))}px "Plus Jakarta Sans", sans-serif`;
          ctx.fillStyle = overlay.colorHex;
          ctx.textAlign = overlay.alignment;
          ctx.textBaseline = 'middle';
          ctx.shadowColor = 'rgba(0,0,0,0.85)';
          ctx.shadowBlur = 10;
          const x =
            overlay.alignment === 'left'
              ? renderW * 0.1
              : overlay.alignment === 'right'
                ? renderW * 0.9
                : renderW * overlay.normalizedX;
          const y = renderH * overlay.normalizedY;
          ctx.fillText(overlay.text, x, y);
          ctx.restore();
        }
      }

      if (elapsedMs >= totalMs) {
        resolve();
      } else {
        window.setTimeout(tick, 33);
      }
    };
    tick();
  });

  for (const el of audioElements) {
    el.pause();
  }
  silentOsc.stop();
  audioCtx.close().catch(() => {});
  recorder.stop();

  const webmBlob = await recordingDone;
  if (signal.aborted) throw new Error('Export cancelled');

  onProgress(78, `Encoding ${preset.exportWidth}x${preset.exportHeight} H.264 MP4...`);

  const res = await fetch(
    `/api/export-mp4?width=${preset.exportWidth}&height=${preset.exportHeight}&fps=30`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/octet-stream' },
      body: webmBlob,
      signal,
    }
  );

  if (!res.ok) {
    throw new Error(`MP4 transcode failed (${res.status})`);
  }

  onProgress(98, 'Finalizing MP4 file...');
  const mp4Blob = await res.blob();
  const blobUrl = URL.createObjectURL(mp4Blob);
  const fileName = `TRISVAX_${preset.exportWidth}x${preset.exportHeight}_${Date.now()}.mp4`;
  onProgress(100, 'Export complete');

  return { blobUrl, fileName };
}

function drawTransformedMedia(
  ctx: CanvasRenderingContext2D,
  media: CanvasImageSource,
  srcW: number,
  srcH: number,
  dstW: number,
  dstH: number,
  clip: VideoClipItem
) {
  ctx.save();
  ctx.translate(dstW / 2, dstH / 2);
  ctx.rotate((clip.rotationDegrees * Math.PI) / 180);

  const zoom = Math.max(1, clip.cropConfig.zoom || 1);
  const rotated = clip.rotationDegrees === 90 || clip.rotationDegrees === 270;
  const effDstW = rotated ? dstH : dstW;
  const effDstH = rotated ? dstW : dstH;

  // Aspect-fill cover scaling without distortion
  const scale = Math.max(effDstW / srcW, effDstH / srcH) * zoom;
  const drawW = srcW * scale;
  const drawH = srcH * scale;
  const panX = (clip.cropConfig.offsetX || 0) * (drawW * 0.25);
  const panY = (clip.cropConfig.offsetY || 0) * (drawH * 0.25);

  ctx.drawImage(media, -drawW / 2 + panX, -drawH / 2 + panY, drawW, drawH);
  ctx.restore();
}

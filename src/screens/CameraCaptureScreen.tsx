import React, { useEffect, useRef, useState } from 'react';
import { Camera, X, Upload } from 'lucide-react';
import {
  extractVideoMetadata,
  extractVideoThumbnails,
} from '../engine/videoMediaUtils';
import { saveMediaBlob } from '../storage/projectDb';
import type { DeviceMediaItem } from '../types/editor';

export function CameraCaptureScreen({
  onBack,
  onCaptured,
}: {
  onBack: () => void;
  onCaptured: (item: DeviceMediaItem) => void;
}) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const [cameraError, setCameraError] = useState<string | null>(null);
  const [recording, setRecording] = useState(false);
  const [elapsedSec, setElapsedSec] = useState(0);

  useEffect(() => {
    let active = true;
    if (!navigator.mediaDevices?.getUserMedia) {
      setCameraError('Camera capture requires a browser camera stream or recording upload.');
      return;
    }

    navigator.mediaDevices
      .getUserMedia({ video: { facingMode: 'environment' }, audio: true })
      .then((stream) => {
        if (!active) {
          stream.getTracks().forEach((t) => t.stop());
          return;
        }
        streamRef.current = stream;
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
        }
      })
      .catch(() => {
        if (active) {
          setCameraError('Camera permission unavailable in this preview. You can upload a video clip instead.');
        }
      });

    return () => {
      active = false;
      streamRef.current?.getTracks().forEach((t) => t.stop());
    };
  }, []);

  useEffect(() => {
    if (!recording) return;
    const id = window.setInterval(() => setElapsedSec((s) => s + 1), 1000);
    return () => window.clearInterval(id);
  }, [recording]);

  const toggleRecord = () => {
    const stream = streamRef.current;
    if (!stream) return;

    if (recording) {
      recorderRef.current?.stop();
      setRecording(false);
    } else {
      const chunks: BlobPart[] = [];
      const mr = new MediaRecorder(stream);
      mr.ondataavailable = (e) => {
        if (e.data.size > 0) chunks.push(e.data);
      };
      mr.onstop = async () => {
        const blob = new Blob(chunks, { type: 'video/webm' });
        const id = `cam_${Date.now()}`;
        await saveMediaBlob(id, blob);
        const url = URL.createObjectURL(blob);
        const meta = await extractVideoMetadata(url);
        const thumbs = await extractVideoThumbnails(url, meta.durationMs, 2);
        onCaptured({
          id,
          url,
          displayName: 'Camera Recording',
          isVideo: true,
          durationMs: meta.durationMs,
          thumbnailUrl: thumbs[0] || '',
          sizeBytes: blob.size,
          mimeType: 'video/webm',
        });
      };
      recorderRef.current = mr;
      setElapsedSec(0);
      mr.start(200);
      setRecording(true);
    }
  };

  return (
    <div className="relative flex min-h-screen w-full flex-col items-center justify-center bg-[#0B0807] p-4">
      <div className="absolute left-4 right-4 top-4 z-10 flex items-center justify-between">
        <button
          type="button"
          onClick={onBack}
          aria-label="Close camera"
          className="flex h-11 w-11 items-center justify-center rounded-full border border-[#2B352D] bg-[#18110D] text-white"
        >
          <X className="h-5 w-5" />
        </button>

        {recording && (
          <div className="rounded-lg bg-[#FF5A5A] px-3 py-1 text-xs font-bold text-white font-mono-tabular">
            REC 0:{String(elapsedSec).padStart(2, '0')}
          </div>
        )}
      </div>

      {cameraError ? (
        <div className="w-full max-w-md rounded-2xl border border-[#2B352D] bg-[#18110D] p-6 text-center">
          <Camera className="mx-auto h-10 w-10 text-[#FF5500]" />
          <h1 className="mt-3 text-lg font-bold text-white">Camera Capture</h1>
          <p className="mt-2 text-sm text-[#9AA69B]">{cameraError}</p>
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            className="mt-5 inline-flex min-h-[46px] w-full items-center justify-center gap-2 rounded-xl bg-[#FF5500] px-5 py-3 text-sm font-bold text-white hover:bg-[#FF8232]"
          >
            <Upload className="h-4 w-4" />
            Choose Camera Video File
          </button>
        </div>
      ) : (
        <>
          <video
            ref={videoRef}
            autoPlay
            muted
            playsInline
            className="max-h-[75vh] w-full max-w-md rounded-2xl border border-[#2B352D] bg-black object-cover"
          />

          <div className="mt-6 flex items-center justify-center">
            <button
              type="button"
              onClick={toggleRecord}
              aria-label={recording ? 'Stop recording' : 'Start recording'}
              className="flex h-[74px] w-[74px] items-center justify-center rounded-full border-4 border-white p-1.5"
            >
              <span
                className={`block h-full w-full bg-[#FF5500] transition-all ${
                  recording ? 'rounded-lg scale-75' : 'rounded-full'
                }`}
              />
            </button>
          </div>
        </>
      )}

      <input
        ref={fileInputRef}
        type="file"
        accept="video/*"
        capture="environment"
        className="hidden"
        onChange={async (e) => {
          const file = e.target.files?.[0];
          if (!file) return;
          const id = `cam_file_${Date.now()}`;
          await saveMediaBlob(id, file);
          const url = URL.createObjectURL(file);
          const meta = await extractVideoMetadata(url);
          const thumbs = await extractVideoThumbnails(url, meta.durationMs, 2);
          onCaptured({
            id,
            url,
            displayName: file.name,
            isVideo: true,
            durationMs: meta.durationMs,
            thumbnailUrl: thumbs[0] || '',
            sizeBytes: file.size,
            mimeType: file.type,
          });
        }}
      />
    </div>
  );
}

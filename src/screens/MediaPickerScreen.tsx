import React, { useEffect, useRef, useState } from 'react';
import { ArrowLeft, Film, ImagePlus, Upload } from 'lucide-react';
import {
  extractImageThumbnail,
  extractVideoMetadata,
  extractVideoThumbnails,
} from '../engine/videoMediaUtils';
import { formatDurationBadge } from '../engine/timelineMath';
import { BUNDLED_DEVICE_SAMPLES, saveMediaBlob } from '../storage/projectDb';
import type { DeviceMediaItem } from '../types/editor';

export function MediaPickerScreen({
  onBack,
  onContinue,
}: {
  onBack: () => void;
  onContinue: (selectedItems: DeviceMediaItem[]) => void;
}) {
  const [items, setItems] = useState<DeviceMediaItem[]>(BUNDLED_DEVICE_SAMPLES);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [importing, setImporting] = useState(false);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    let active = true;
    Promise.all(
      BUNDLED_DEVICE_SAMPLES.map(async (sample) => {
        const thumbs = await extractVideoThumbnails(sample.url, sample.durationMs, 1);
        return { ...sample, thumbnailUrl: thumbs[0] || '' };
      })
    ).then((loaded) => {
      if (active) {
        setItems(loaded);
      }
    });
    return () => {
      active = false;
    };
  }, []);

  const handleFilesSelected = async (fileList: FileList) => {
    setImporting(true);
    try {
      const added: DeviceMediaItem[] = [];
      for (const file of Array.from(fileList)) {
        const id = `local_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`;
        await saveMediaBlob(id, file);
        const url = URL.createObjectURL(file);
        const isVideo = file.type.startsWith('video/');
        if (isVideo) {
          const meta = await extractVideoMetadata(url);
          const thumbs = await extractVideoThumbnails(url, meta.durationMs, 1);
          added.push({
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
          added.push({
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
      setItems((prev) => [...added, ...prev]);
      setSelectedIds((prev) => [...prev, ...added.map((a) => a.id)]);
    } finally {
      setImporting(false);
    }
  };

  const toggleSelect = (id: string) => {
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]
    );
  };

  const handleContinue = () => {
    const ordered = selectedIds
      .map((id) => items.find((item) => item.id === id))
      .filter((item): item is DeviceMediaItem => Boolean(item));
    if (ordered.length > 0) {
      onContinue(ordered);
    }
  };

  return (
    <div className="flex min-h-screen w-full flex-col bg-[#0B0807]">
      <header className="sticky top-0 z-20 flex items-center justify-between border-b border-[#2B352D] bg-[#0B0807]/95 px-4 py-3 backdrop-blur-md">
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={onBack}
            aria-label="Back to projects"
            className="flex h-11 w-11 items-center justify-center rounded-xl text-white hover:bg-[#18110D]"
          >
            <ArrowLeft className="h-5 w-5" />
          </button>
          <h1 className="text-lg font-bold text-white">Select Media</h1>
        </div>

        <button
          type="button"
          onClick={() => fileInputRef.current?.click()}
          disabled={importing}
          className="flex min-h-[42px] items-center gap-2 rounded-xl border border-[#2B352D] bg-[#18110D] px-3.5 py-2 text-xs font-semibold text-white hover:border-[#FF5500] transition-colors"
        >
          <Upload className="h-4 w-4 text-[#FF5500]" />
          <span>{importing ? 'Importing...' : 'Import from Device'}</span>
        </button>
      </header>

      <main className="mx-auto w-full max-w-5xl flex-1 p-4">
        <div className="grid grid-cols-3 gap-2.5 sm:grid-cols-4 md:grid-cols-5">
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            className="flex aspect-[0.82] flex-col items-center justify-center gap-2 rounded-xl border border-dashed border-[#2B352D] bg-[#18110D] p-3 text-center hover:border-[#FF5500] transition-colors"
          >
            <ImagePlus className="h-6 w-6 text-[#FF5500]" />
            <span className="text-xs font-medium text-[#9AA69B]">
              Add Videos / Photos
            </span>
          </button>

          {items.map((item) => {
            const orderIdx = selectedIds.indexOf(item.id);
            const selected = orderIdx >= 0;
            return (
              <button
                key={item.id}
                type="button"
                onClick={() => toggleSelect(item.id)}
                aria-label={`${selected ? 'Deselect' : 'Select'} ${item.displayName}`}
                className={`relative aspect-[0.82] overflow-hidden rounded-xl border bg-[#18110D] text-left transition-all ${
                  selected ? 'border-2 border-[#FF5500]' : 'border-[#2B352D] hover:border-[#667568]'
                }`}
              >
                {item.thumbnailUrl ? (
                  <img
                    src={item.thumbnailUrl}
                    alt={item.displayName}
                    referrerPolicy="no-referrer"
                    className="h-full w-full object-cover"
                  />
                ) : (
                  <div className="flex h-full w-full items-center justify-center bg-[#221711]">
                    <Film className="h-6 w-6 text-[#9AA69B]" />
                  </div>
                )}

                <div className="pointer-events-none absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/80 to-transparent px-2 py-1.5">
                  <p className="truncate text-[11px] font-medium text-white">
                    {item.displayName}
                  </p>
                  {item.isVideo && (
                    <span className="text-[10px] text-[#9AA69B] font-mono-tabular">
                      {formatDurationBadge(item.durationMs)}
                    </span>
                  )}
                </div>

                <span
                  className={`absolute right-2 top-2 flex h-6 w-6 items-center justify-center rounded-full border text-[11px] font-bold ${
                    selected
                      ? 'border-white bg-[#FF5500] text-white'
                      : 'border-white/80 bg-black/50 text-transparent'
                  }`}
                >
                  {selected ? orderIdx + 1 : ''}
                </span>
              </button>
            );
          })}
        </div>
      </main>

      <footer className="sticky bottom-0 z-20 border-t border-[#2B352D] bg-[#18110D] px-4 py-3">
        <div className="mx-auto flex w-full max-w-5xl items-center justify-between">
          <span className="text-sm text-[#9AA69B] font-mono-tabular">
            {selectedIds.length} selected
          </span>
          <button
            type="button"
            disabled={selectedIds.length === 0}
            onClick={handleContinue}
            className="min-h-[44px] rounded-xl bg-[#FF5500] px-6 py-2.5 text-sm font-bold text-white transition-colors hover:bg-[#FF8232] disabled:cursor-not-allowed disabled:opacity-45"
          >
            Continue
          </button>
        </div>
      </footer>

      <input
        ref={fileInputRef}
        type="file"
        accept="video/*,image/*"
        multiple
        className="hidden"
        onChange={(e) => {
          if (e.target.files && e.target.files.length > 0) {
            handleFilesSelected(e.target.files);
          }
          e.target.value = '';
        }}
      />
    </div>
  );
}

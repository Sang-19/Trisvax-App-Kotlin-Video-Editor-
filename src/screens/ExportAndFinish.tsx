import React from 'react';
import { Check, Download, Home } from 'lucide-react';
import { TrisvaxBrandBadge } from '../components/TrisvaxBrand';
import { ASPECT_PRESETS, type AspectRatioPreset, type ExportProgressState } from '../types/editor';

export function ExportProgressModal({
  state,
  aspectRatio,
  onCancel,
  onDismissError,
}: {
  state: ExportProgressState;
  aspectRatio: AspectRatioPreset;
  onCancel: () => void;
  onDismissError: () => void;
}) {
  if (state.status === 'idle' || state.status === 'success') return null;

  const spec = ASPECT_PRESETS[aspectRatio];

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 p-4"
      role="dialog"
      aria-modal="true"
      aria-label="Exporting video"
    >
      <div className="w-full max-w-sm rounded-2xl border border-[#2B352D] bg-[#18110D] p-6">
        {state.status === 'error' ? (
          <>
            <h2 className="text-lg font-bold text-[#FF5A5A]">Export Failed</h2>
            <p className="mt-2 text-sm text-[#9AA69B]">
              {state.errorMessage || 'An unexpected error occurred during 1080p MP4 export.'}
            </p>
            <button
              type="button"
              onClick={onDismissError}
              className="mt-5 min-h-[44px] w-full rounded-xl bg-[#FF5500] text-sm font-bold text-white"
            >
              Close
            </button>
          </>
        ) : (
          <>
            <h2 className="text-lg font-bold text-white">Exporting 1080p MP4</h2>
            <p className="mt-1.5 text-xs text-[#9AA69B] font-mono-tabular">
              {spec.exportWidth} × {spec.exportHeight} · 30 FPS · H.264/AAC
            </p>
            <p className="mt-3 text-sm text-white">{state.message}</p>

            <div className="mt-3 h-2.5 w-full overflow-hidden rounded-full bg-[#221711]">
              <div
                style={{ width: `${Math.min(100, Math.max(0, state.progressPercent))}%` }}
                className="h-full bg-[#FF5500] transition-all duration-200"
              />
            </div>
            <p className="mt-2 text-right text-xs font-bold text-[#FF8232] font-mono-tabular">
              {state.progressPercent}%
            </p>

            <button
              type="button"
              onClick={onCancel}
              className="mt-4 min-h-[44px] w-full rounded-xl border border-[#2B352D] bg-[#221711] text-sm font-semibold text-white hover:border-[#FF5500]"
            >
              Cancel Export
            </button>
          </>
        )}
      </div>
    </div>
  );
}

export function SaveConfirmationScreen({
  downloadUrl,
  downloadFileName,
  onGoToHome,
}: {
  downloadUrl?: string;
  downloadFileName?: string;
  onGoToHome: () => void;
}) {
  return (
    <main className="relative flex min-h-screen w-full flex-col justify-between bg-[#0B0807] px-5 py-5 sm:px-8 sm:py-7">
      <header className="flex items-center justify-between">
        <TrisvaxBrandBadge />
      </header>

      <section className="mx-auto my-auto flex w-full max-w-[420px] flex-col items-center py-8">
        <div className="flex w-full flex-col items-center rounded-[18px] border border-[#2B352D] bg-[#18110D] p-7 text-center">
          <div className="flex h-[74px] w-[74px] items-center justify-center rounded-full bg-[#FF5500]/20">
            <span className="flex h-14 w-14 items-center justify-center rounded-full bg-[#FF5500] text-white shadow-[0_0_24px_rgba(255,85,0,0.45)]">
              <Check className="h-8 w-8 stroke-[3]" />
            </span>
          </div>

          <h1 className="mt-5 text-[20px] font-bold text-white">
            Your project has been saved
          </h1>

          <button
            type="button"
            onClick={onGoToHome}
            className="mt-6 flex min-h-[52px] w-full items-center justify-center gap-2 rounded-[12px] bg-[#FF5500] px-6 py-3 text-[16px] font-bold text-white transition-colors hover:bg-[#FF8232]"
          >
            <Home className="h-5 w-5" />
            <span>Go to Home</span>
          </button>

          {downloadUrl && (
            <a
              href={downloadUrl}
              download={downloadFileName || 'TRISVAX_1080p.mp4'}
              className="mt-3 flex min-h-[44px] w-full items-center justify-center gap-2 rounded-[12px] border border-[#2B352D] bg-[#221711] px-4 py-2.5 text-xs font-semibold text-white hover:border-[#FF5500]"
            >
              <Download className="h-4 w-4 text-[#FF5500]" />
              <span>Download Exported 1080p MP4</span>
            </a>
          )}
        </div>
      </section>

      <div />
    </main>
  );
}

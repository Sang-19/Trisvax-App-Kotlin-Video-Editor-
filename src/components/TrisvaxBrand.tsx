import React from 'react';
import { Download, Smartphone, X, CheckCircle2, FolderCode } from 'lucide-react';

export function TrisvaxBrandBadge({ className = '' }: { className?: string }) {
  return (
    <div
      className={`inline-flex items-center gap-2.5 rounded-[10px] border border-[#2B352D] bg-[#18110D] px-2.5 py-1.5 ${className}`}
    >
      <span className="flex h-7 w-7 items-center justify-center rounded-[7px] bg-[#FF5500] text-[17px] font-extrabold leading-none text-[#0B0807]">
        T
      </span>
      <span className="text-[14px] font-bold tracking-[0.08em] text-white whitespace-nowrap">
        TRISVAX
      </span>
      <span className="text-[14px] font-medium tracking-[0.09em] text-[#9AA69B] whitespace-nowrap">
        STUDIO
      </span>
    </div>
  );
}

export function TrisvaxClapperLogo({ className = '' }: { className?: string }) {
  return (
    <span
      className={`inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-[10px] bg-[#FF5500] text-[#0B0807] ${className}`}
      aria-hidden="true"
    >
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
        <path
          d="M4 6.5L19.5 3.5L20.5 7.5L4.5 10.5L4 6.5Z"
          fill="#0B0807"
          stroke="#0B0807"
          strokeWidth="1.2"
          strokeLinejoin="round"
        />
        <path d="M7.5 5.8L9.5 9.5M12.5 4.8L14.5 8.5M17 4L18.5 7.5" stroke="#FF5500" strokeWidth="1.4" />
        <rect x="4" y="10.5" width="16.5" height="10" rx="2" fill="#0B0807" />
        <path d="M10.5 13.3L15.2 15.5L10.5 17.7V13.3Z" fill="#FF5500" />
      </svg>
    </span>
  );
}

export function AndroidSourceModal({
  open,
  onClose,
}: {
  open: boolean;
  onClose: () => void;
}) {
  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="android-source-title"
    >
      <div className="w-full max-w-lg rounded-2xl border border-[#2B352D] bg-[#18110D] p-6">
        <div className="flex items-start justify-between gap-4">
          <div className="flex items-center gap-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-[#221711] text-[#FF5500] border border-[#2B352D]">
              <Smartphone className="h-5 w-5" />
            </span>
            <div>
              <h2 id="android-source-title" className="text-lg font-bold text-white">
                Native Android Project
              </h2>
              <p className="text-xs text-[#9AA69B]">
                Kotlin · Jetpack Compose · Room · CameraX · Media3 Transformer
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close Android export dialog"
            className="flex h-11 w-11 items-center justify-center rounded-full border border-[#2B352D] bg-[#221711] text-white hover:border-[#FF5500] transition-colors"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="mt-5 space-y-3 rounded-xl border border-[#2B352D] bg-[#0B0807] p-4 text-sm text-[#9AA69B]">
          <div className="flex items-start gap-2.5">
            <FolderCode className="mt-0.5 h-4 w-4 shrink-0 text-[#FF5500]" />
            <span>
              Includes full <code className="text-white">com.trisvax.studio</code> source code and{' '}
              <code className="text-white">app/src/main/assets/music/</code> demo tracks.
            </span>
          </div>
          <div className="flex items-start gap-2.5">
            <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-[#FF5500]" />
            <span>
              Open <code className="text-white">android-project</code> in Android Studio, enable USB Debugging on your phone, and click Run to install directly.
            </span>
          </div>
        </div>

        <div className="mt-5 flex flex-col sm:flex-row gap-3">
          <a
            href="/api/android-project.zip"
            download="trisvax-studio-android.zip"
            className="flex min-h-[48px] flex-1 items-center justify-center gap-2 rounded-xl bg-[#FF5500] px-5 py-3 text-sm font-bold text-white hover:bg-[#FF8232] transition-colors whitespace-nowrap"
          >
            <Download className="h-4 w-4" />
            Download Android Project (.zip)
          </a>
          <button
            type="button"
            onClick={onClose}
            className="min-h-[48px] rounded-xl border border-[#2B352D] bg-[#221711] px-5 py-3 text-sm font-medium text-white hover:border-[#FF5500] transition-colors"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
}

import React from 'react';
import { ArrowRight } from 'lucide-react';
import { TrisvaxBrandBadge } from '../components/TrisvaxBrand';

export function WelcomeScreen({
  onOpenStudio,
}: {
  onOpenStudio: () => void;
}) {
  return (
    <main className="relative flex min-h-screen w-full flex-col justify-between bg-[#0B0807] px-5 py-5 sm:px-8 sm:py-7">
      <header className="flex items-center justify-between">
        <TrisvaxBrandBadge />
      </header>

      <section className="mx-auto my-auto flex w-full max-w-[420px] flex-col items-center py-8">
        <div className="w-full rounded-[18px] border border-[#2B352D] bg-[#18110D] p-6 sm:p-7">
          <h1 className="text-[24px] font-bold tracking-tight text-white">
            Start creating
          </h1>

          <button
            type="button"
            onClick={onOpenStudio}
            className="mt-5 flex min-h-[54px] w-full items-center justify-center gap-2 rounded-[12px] bg-[#FF5500] px-6 py-3.5 text-[16px] font-bold text-white transition-colors hover:bg-[#FF8232] active:scale-[0.99] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#FF8232]"
          >
            <span>Open your studio</span>
            <ArrowRight className="h-5 w-5" />
          </button>
        </div>
      </section>

      <footer className="mx-auto w-full max-w-[420px]">
        <div className="rounded-[12px] border border-[#2B352D] bg-[#18110D] px-4 py-3 text-center text-[12px] text-[#9AA69B]">
          No account. No uploads. Your media stays on your device.
        </div>
      </footer>
    </main>
  );
}

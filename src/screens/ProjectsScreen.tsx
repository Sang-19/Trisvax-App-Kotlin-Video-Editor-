import React, { useMemo, useRef, useState } from 'react';
import {
  Camera,
  FolderOpen,
  MoreVertical,
  Film,
  Plus,
  Search,
  Video,
  X,
} from 'lucide-react';
import { TrisvaxClapperLogo } from '../components/TrisvaxBrand';
import {
  formatDurationBadge,
  formatRelativeDate,
  formatStorageSize,
} from '../engine/timelineMath';
import type { ProjectRecord } from '../types/editor';

export function ProjectsScreen({
  projects,
  onOpenProject,
  onRenameProject,
  onDeleteProject,
  onQuickImportFiles,
  onOpenCamera,
  onOpenGallery,
}: {
  projects: ProjectRecord[];
  onOpenProject: (projectId: string) => void;
  onRenameProject: (projectId: string, newName: string) => void;
  onDeleteProject: (projectId: string) => void;
  onQuickImportFiles: (files: FileList) => void;
  onOpenCamera: () => void;
  onOpenGallery: () => void;
}) {
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [createMenuOpen, setCreateMenuOpen] = useState(false);
  const [openOverflowId, setOpenOverflowId] = useState<string | null>(null);
  const [renameTarget, setRenameTarget] = useState<ProjectRecord | null>(null);
  const [renameValue, setRenameValue] = useState('');
  const [deleteTarget, setDeleteTarget] = useState<ProjectRecord | null>(null);

  const quickVideoInputRef = useRef<HTMLInputElement | null>(null);

  const filteredProjects = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return projects;
    return projects.filter((p) => p.name.toLowerCase().includes(q));
  }, [projects, searchQuery]);

  const totalStorageBytes = useMemo(
    () => projects.reduce((sum, p) => sum + (p.storageSizeBytes || 0), 0),
    [projects]
  );

  return (
    <div className="relative min-h-screen w-full bg-[#0B0807] pb-28">
      <div className="mx-auto w-full max-w-6xl px-4 pt-5 sm:px-6 lg:px-8">
        {/* Header matching Home_Page_92a909a8.png */}
        <header className="flex items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <TrisvaxClapperLogo />
            <h1 className="text-[26px] font-bold tracking-tight text-white">
              Projects
            </h1>
          </div>

          <button
            type="button"
            onClick={() => {
              setSearchOpen((v) => !v);
              if (searchOpen) setSearchQuery('');
            }}
            aria-label={searchOpen ? 'Close project search' : 'Search projects'}
            className="flex h-11 w-11 items-center justify-center rounded-xl border border-[#2B352D] bg-[#18110D] text-white hover:border-[#FF5500] transition-colors"
          >
            {searchOpen ? <X className="h-5 w-5" /> : <Search className="h-5 w-5" />}
          </button>
        </header>

        <p className="mt-1.5 text-[13px] text-[#9AA69B] font-mono-tabular">
          {projects.length} {projects.length === 1 ? 'project' : 'projects'} ·{' '}
          {formatStorageSize(totalStorageBytes)}
        </p>

        {searchOpen && (
          <div className="mt-3">
            <label htmlFor="project-search-input" className="sr-only">
              Search projects by name
            </label>
            <input
              id="project-search-input"
              type="search"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search projects by name..."
              autoFocus
              className="min-h-[44px] w-full rounded-xl border border-[#2B352D] bg-[#18110D] px-4 py-2.5 text-sm text-white placeholder:text-[#667568] focus:border-[#FF5500] focus:outline-none"
            />
          </div>
        )}

        {/* Project Cards Grid */}
        {filteredProjects.length === 0 ? (
          <div className="mt-16 flex flex-col items-center justify-center rounded-2xl border border-[#2B352D] bg-[#18110D] px-6 py-14 text-center">
            <p className="text-base font-semibold text-white">No projects found</p>
            <p className="mt-1 text-sm text-[#9AA69B]">
              Tap the orange + button to create a new video project.
            </p>
          </div>
        ) : (
          <div className="mt-4 grid grid-cols-2 gap-3.5 sm:grid-cols-3 lg:grid-cols-4">
            {filteredProjects.map((project) => (
              <article
                key={project.id}
                className="group relative flex flex-col overflow-hidden rounded-[16px] border border-[#2B352D] bg-[#18110D] transition-colors hover:border-[#FF5500]/60"
              >
                <div className="relative aspect-[0.95] w-full bg-[#221711]">
                  <button
                    type="button"
                    onClick={() => onOpenProject(project.id)}
                    aria-label={`Open project ${project.name}`}
                    className="block h-full w-full text-left focus-visible:outline-2 focus-visible:outline-[#FF5500]"
                  >
                    {project.thumbnailUrl ? (
                      <img
                        src={project.thumbnailUrl}
                        alt={project.name}
                        referrerPolicy="no-referrer"
                        className="h-full w-full object-cover"
                      />
                    ) : (
                      <div className="flex h-full w-full items-center justify-center bg-[#221711]">
                        <Film className="h-8 w-8 text-[#FF5500]" />
                      </div>
                    )}
                    <div className="pointer-events-none absolute inset-x-0 bottom-0 h-14 bg-gradient-to-t from-black/75 to-transparent" />
                  </button>

                  {/* Duration indicator top-left */}
                  <div className="pointer-events-none absolute left-2.5 top-2.5 inline-flex items-center gap-1.5 rounded-lg bg-black/65 px-2 py-1 text-[11px] font-medium text-white font-mono-tabular">
                    <Film className="h-3 w-3 text-white" />
                    <span>{formatDurationBadge(project.durationMs)}</span>
                  </div>

                  {/* Overflow menu button top-right */}
                  <div className="absolute right-1.5 top-1.5">
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        setOpenOverflowId((prev) => (prev === project.id ? null : project.id));
                      }}
                      aria-label={`Project options for ${project.name}`}
                      className="flex h-10 w-10 items-center justify-center rounded-full bg-black/45 text-white hover:bg-black/75 transition-colors"
                    >
                      <MoreVertical className="h-4 w-4" />
                    </button>

                    {openOverflowId === project.id && (
                      <div className="absolute right-0 top-11 z-30 w-36 overflow-hidden rounded-xl border border-[#2B352D] bg-[#221711] shadow-xl">
                        <button
                          type="button"
                          onClick={() => {
                            setOpenOverflowId(null);
                            setRenameTarget(project);
                            setRenameValue(project.name);
                          }}
                          className="block w-full px-3.5 py-2.5 text-left text-xs font-medium text-white hover:bg-[#18110D]"
                        >
                          Rename
                        </button>
                        <button
                          type="button"
                          onClick={() => {
                            setOpenOverflowId(null);
                            setDeleteTarget(project);
                          }}
                          className="block w-full px-3.5 py-2.5 text-left text-xs font-medium text-[#FF5A5A] hover:bg-[#18110D]"
                        >
                          Delete
                        </button>
                      </div>
                    )}
                  </div>
                </div>

                {/* Card footer matching Home_Page_92a909a8.png */}
                <button
                  type="button"
                  onClick={() => onOpenProject(project.id)}
                  className="flex flex-col items-start px-3 py-2.5 text-left"
                >
                  <span className="w-full truncate text-[14px] font-semibold text-white">
                    {project.name}
                  </span>
                  <span className="mt-1.5 h-[2px] w-6 rounded-full bg-[#FF5500]" />
                  <span className="mt-1.5 text-[11px] text-[#9AA69B] font-mono-tabular">
                    {formatRelativeDate(project.lastModifiedAt)} ·{' '}
                    {formatStorageSize(project.storageSizeBytes)}
                  </span>
                </button>
              </article>
            ))}
          </div>
        )}
      </div>

      {/* Backdrop for create popup */}
      {createMenuOpen && (
        <div
          className="fixed inset-0 z-30 bg-black/45"
          onClick={() => setCreateMenuOpen(false)}
          aria-hidden="true"
        />
      )}

      {/* Floating + button & 3-option popup matching Create_Button_399036ef.png */}
      <div className="fixed bottom-6 right-5 z-40 flex flex-col items-end sm:right-8">
        {createMenuOpen && (
          <div
            role="menu"
            aria-label="Create new project"
            className="mb-3 w-48 overflow-hidden rounded-2xl border border-[#2B352D] bg-[#18110D] py-1.5 shadow-2xl"
          >
            <button
              type="button"
              role="menuitem"
              onClick={() => {
                setCreateMenuOpen(false);
                quickVideoInputRef.current?.click();
              }}
              className="flex min-h-[46px] w-full items-center gap-3.5 px-4 py-2.5 text-left text-[15px] font-medium text-white hover:bg-[#221711] transition-colors"
            >
              <Video className="h-5 w-5 text-[#FF5500]" />
              <span>Video</span>
            </button>

            <button
              type="button"
              role="menuitem"
              onClick={() => {
                setCreateMenuOpen(false);
                onOpenCamera();
              }}
              className="flex min-h-[46px] w-full items-center gap-3.5 px-4 py-2.5 text-left text-[15px] font-medium text-white hover:bg-[#221711] transition-colors"
            >
              <Camera className="h-5 w-5 text-[#FF5500]" />
              <span>Camera</span>
            </button>

            <button
              type="button"
              role="menuitem"
              onClick={() => {
                setCreateMenuOpen(false);
                onOpenGallery();
              }}
              className="flex min-h-[46px] w-full items-center gap-3.5 px-4 py-2.5 text-left text-[15px] font-medium text-white hover:bg-[#221711] transition-colors"
            >
              <FolderOpen className="h-5 w-5 text-[#FF5500]" />
              <span>Gallery</span>
            </button>
          </div>
        )}

        <button
          type="button"
          onClick={() => setCreateMenuOpen((v) => !v)}
          aria-label="Create new project"
          aria-expanded={createMenuOpen}
          className="flex h-[58px] w-[58px] items-center justify-center rounded-[18px] bg-[#FF5500] text-white shadow-lg transition-transform hover:bg-[#FF8232] active:scale-95"
        >
          {createMenuOpen ? <X className="h-7 w-7" /> : <Plus className="h-7 w-7" />}
        </button>
      </div>

      <input
        ref={quickVideoInputRef}
        type="file"
        accept="video/*,image/*"
        multiple
        className="hidden"
        onChange={(e) => {
          if (e.target.files && e.target.files.length > 0) {
            onQuickImportFiles(e.target.files);
          }
          e.target.value = '';
        }}
      />

      {/* Rename Modal */}
      {renameTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4" role="dialog" aria-modal="true">
          <div className="w-full max-w-sm rounded-2xl border border-[#2B352D] bg-[#18110D] p-5">
            <h2 className="text-base font-bold text-white">Rename project</h2>
            <input
              type="text"
              value={renameValue}
              onChange={(e) => setRenameValue(e.target.value)}
              className="mt-3 min-h-[44px] w-full rounded-xl border border-[#2B352D] bg-[#0B0807] px-3.5 py-2 text-sm text-white focus:border-[#FF5500] focus:outline-none"
              autoFocus
            />
            <div className="mt-4 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setRenameTarget(null)}
                className="min-h-[40px] rounded-xl px-4 text-sm font-medium text-[#9AA69B] hover:text-white"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => {
                  onRenameProject(renameTarget.id, renameValue);
                  setRenameTarget(null);
                }}
                className="min-h-[40px] rounded-xl bg-[#FF5500] px-4 text-sm font-semibold text-white hover:bg-[#FF8232]"
              >
                Save
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Delete Modal */}
      {deleteTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4" role="dialog" aria-modal="true">
          <div className="w-full max-w-sm rounded-2xl border border-[#2B352D] bg-[#18110D] p-5">
            <h2 className="text-base font-bold text-white">Delete project?</h2>
            <p className="mt-2 text-sm text-[#9AA69B]">
              Remove &ldquo;{deleteTarget.name}&rdquo; and its local editing timeline?
            </p>
            <div className="mt-4 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setDeleteTarget(null)}
                className="min-h-[40px] rounded-xl px-4 text-sm font-medium text-[#9AA69B] hover:text-white"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => {
                  onDeleteProject(deleteTarget.id);
                  setDeleteTarget(null);
                }}
                className="min-h-[40px] rounded-xl bg-[#FF5A5A] px-4 text-sm font-semibold text-white"
              >
                Delete
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
  );
}

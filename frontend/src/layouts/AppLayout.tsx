import { Link, Outlet } from 'react-router-dom';
import { useTheme } from '../hooks/useTheme';

export function AppLayout() {
  const { theme, toggle } = useTheme();

  return (
    <div className="min-h-screen bg-zinc-50 text-zinc-900 dark:bg-zinc-950 dark:text-zinc-100">
      <div className="pointer-events-none fixed inset-0 bg-[radial-gradient(ellipse_at_top,rgba(24,24,27,0.05),transparent_55%)] dark:bg-[radial-gradient(ellipse_at_top,rgba(255,255,255,0.06),transparent_50%)]" />
      <div className="relative mx-auto flex min-h-screen w-full max-w-xl flex-col px-4">
        <header className="flex items-center justify-between py-6">
          <Link to="/" className="flex items-center gap-2 text-sm font-semibold tracking-tight">
            <span className="flex h-7 w-7 items-center justify-center rounded-lg border border-zinc-200 bg-white text-xs dark:border-zinc-800 dark:bg-zinc-900">
              AS
            </span>
            All Share
          </Link>
          <button
            type="button"
            onClick={toggle}
            aria-label={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
            className="rounded-lg border border-zinc-200 px-2.5 py-1.5 text-xs font-medium text-zinc-600 dark:border-zinc-800 dark:text-zinc-300"
          >
            {theme === 'dark' ? 'Light' : 'Dark'}
          </button>
        </header>
        <main className="flex-1 pb-10">
          <Outlet />
        </main>
        <footer className="pb-8 text-center text-xs text-zinc-400">
          Shares expire on their own. No account required.
        </footer>
      </div>
    </div>
  );
}

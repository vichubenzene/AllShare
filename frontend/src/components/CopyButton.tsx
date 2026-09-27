import { useToast } from './Toast';

export function CopyButton({ value, label = 'Copy' }: { value: string; label?: string }) {
  const toast = useToast();

  return (
    <button
      type="button"
      onClick={() => {
        navigator.clipboard.writeText(value).then(
          () => toast('Copied'),
          () => toast('Could not copy', 'err'),
        );
      }}
      className="rounded-lg border border-zinc-200 px-3 py-1.5 text-sm font-medium text-zinc-700 transition hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-200 dark:hover:bg-zinc-800"
    >
      {label}
    </button>
  );
}

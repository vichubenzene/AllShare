import { useState } from 'react';
import { formatBytes } from '../utils/format';

export function FileDropzone({
  file,
  onFile,
}: {
  file: File | null;
  onFile: (file: File | null) => void;
}) {
  const [dragging, setDragging] = useState(false);

  return (
    <div
      onDragOver={(event) => {
        event.preventDefault();
        setDragging(true);
      }}
      onDragLeave={() => setDragging(false)}
      onDrop={(event) => {
        event.preventDefault();
        setDragging(false);
        const next = event.dataTransfer.files[0];
        if (next) {
          onFile(next);
        }
      }}
      className={`flex min-h-56 flex-col items-center justify-center rounded-xl border border-dashed px-6 text-center transition ${
        dragging
          ? 'border-zinc-900 bg-zinc-50 dark:border-zinc-100 dark:bg-zinc-800'
          : 'border-zinc-300 bg-zinc-50/60 dark:border-zinc-700 dark:bg-zinc-950/40'
      }`}
    >
      {file ? (
        <div className="space-y-3">
          <p className="max-w-xs truncate text-sm font-medium">{file.name}</p>
          <p className="text-xs text-zinc-500">{formatBytes(file.size)}</p>
          <button
            type="button"
            onClick={() => onFile(null)}
            className="text-sm text-zinc-500 underline-offset-2 hover:underline"
          >
            Remove
          </button>
        </div>
      ) : (
        <>
          <p className="text-sm font-medium">Drag and drop a file here</p>
          <p className="my-3 text-xs uppercase tracking-wide text-zinc-400">or</p>
          <label className="cursor-pointer rounded-lg border border-zinc-300 bg-white px-3 py-1.5 text-sm font-medium dark:border-zinc-700 dark:bg-zinc-900">
            Choose file
            <input
              type="file"
              className="sr-only"
              onChange={(event) => {
                const next = event.target.files?.[0] ?? null;
                onFile(next);
                event.target.value = '';
              }}
            />
          </label>
        </>
      )}
    </div>
  );
}

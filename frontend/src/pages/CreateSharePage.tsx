import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { createTextShare, uploadFileShare } from '../api/client';
import { ApiError } from '../api/client';
import { Button } from '../components/Button';
import { FileDropzone } from '../components/FileDropzone';
import { useToast } from '../components/Toast';
import { EXPIRATIONS, MAX_FILE_BYTES } from '../types/share';
import { saveCreated } from '../utils/session';

type Mode = 'text' | 'file';

export function CreateSharePage() {
  const navigate = useNavigate();
  const toast = useToast();
  const [mode, setMode] = useState<Mode>('text');
  const [content, setContent] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [expirationMinutes, setExpirationMinutes] = useState<number>(60);
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [progress, setProgress] = useState(0);
  const [error, setError] = useState<string | null>(null);

  const canSubmit = mode === 'text' ? content.trim().length > 0 : file !== null;

  function chooseFile(next: File | null) {
    if (next && next.size > MAX_FILE_BYTES) {
      setError('File exceeds the 50 MB limit.');
      toast('File exceeds the 50 MB limit.', 'err');
      return;
    }
    setError(null);
    setFile(next);
  }

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (!canSubmit || submitting) {
      return;
    }
    setSubmitting(true);
    setError(null);
    setProgress(0);
    try {
      const created =
        mode === 'text'
          ? await createTextShare({ content, expirationMinutes, password })
          : await uploadFileShare(file as File, expirationMinutes, password, setProgress);
      saveCreated(created);
      navigate(`/share-created/${created.token}`);
    } catch (err) {
      const message = err instanceof ApiError ? err.message : 'Could not create the share.';
      setError(message);
      toast(message, 'err');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="rounded-2xl border border-zinc-200 bg-white p-6 shadow-sm dark:border-zinc-800 dark:bg-zinc-900">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold tracking-tight">Temporary Share</h1>
        <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
          Send a note or a file. It disappears when the timer runs out.
        </p>
      </div>

      <div className="mb-4 grid grid-cols-2 rounded-lg bg-zinc-100 p-1 dark:bg-zinc-950">
        {(['text', 'file'] as const).map((item) => (
          <button
            key={item}
            type="button"
            onClick={() => {
              setMode(item);
              setError(null);
            }}
            className={`h-9 rounded-md text-sm font-medium capitalize transition ${
              mode === item
                ? 'bg-white text-zinc-900 shadow-sm dark:bg-zinc-800 dark:text-zinc-100'
                : 'text-zinc-500'
            }`}
          >
            {item}
          </button>
        ))}
      </div>

      <form onSubmit={onSubmit} className="space-y-4">
        {mode === 'text' ? (
          <label className="block">
            <span className="sr-only">Text</span>
            <textarea
              value={content}
              onChange={(event) => setContent(event.target.value)}
              placeholder="Paste your text here..."
              className="min-h-56 w-full resize-y rounded-xl border border-zinc-200 bg-zinc-50 px-3 py-3 font-mono text-sm outline-none ring-zinc-400 focus:ring-2 dark:border-zinc-700 dark:bg-zinc-950"
            />
          </label>
        ) : (
          <FileDropzone file={file} onFile={chooseFile} />
        )}

        {mode === 'file' && submitting && progress > 0 ? (
          <div className="h-1.5 overflow-hidden rounded-full bg-zinc-100 dark:bg-zinc-800">
            <div className="h-full bg-zinc-900 transition-all dark:bg-zinc-100" style={{ width: `${progress}%` }} />
          </div>
        ) : null}

        <label className="block text-sm">
          <span className="mb-1.5 block text-zinc-500">Expiration</span>
          <select
            value={expirationMinutes}
            onChange={(event) => setExpirationMinutes(Number(event.target.value))}
            className="h-11 w-full rounded-lg border border-zinc-200 bg-white px-3 outline-none ring-zinc-400 focus:ring-2 dark:border-zinc-700 dark:bg-zinc-950"
          >
            {EXPIRATIONS.map((option) => (
              <option key={option.minutes} value={option.minutes}>
                {option.label}
              </option>
            ))}
          </select>
        </label>

        <label className="block text-sm">
          <span className="mb-1.5 block text-zinc-500">Password</span>
          <div className="relative">
            <input
              type={showPassword ? 'text' : 'password'}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="Optional password"
              autoComplete="new-password"
              maxLength={200}
              className="h-11 w-full rounded-lg border border-zinc-200 bg-white px-3 pr-16 outline-none ring-zinc-400 focus:ring-2 dark:border-zinc-700 dark:bg-zinc-950"
            />
            <button
              type="button"
              onClick={() => setShowPassword((value) => !value)}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-zinc-500"
            >
              {showPassword ? 'Hide' : 'Show'}
            </button>
          </div>
        </label>

        {error ? <p className="text-sm text-red-600 dark:text-red-400">{error}</p> : null}

        <Button type="submit" disabled={!canSubmit || submitting} className="w-full">
          {submitting ? (mode === 'file' ? `Uploading ${progress}%` : 'Creating…') : 'Create Share'}
        </Button>
      </form>
    </section>
  );
}

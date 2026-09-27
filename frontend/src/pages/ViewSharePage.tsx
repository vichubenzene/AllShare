import { useState } from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ApiError, downloadShare, fetchShare, revokeShare } from '../api/client';
import { CopyButton } from '../components/CopyButton';
import { Countdown } from '../components/Countdown';
import { LoadingCard, StateCard } from '../components/StateCard';
import { useToast } from '../components/Toast';
import { fileKind, formatBytes } from '../utils/format';
import { clearAccess, clearCreated, loadCreated } from '../utils/session';

export function ViewSharePage() {
  const { token = '' } = useParams();
  const toast = useToast();
  const created = loadCreated(token);
  const [downloading, setDownloading] = useState(false);
  const [revoked, setRevoked] = useState(false);
  const [confirming, setConfirming] = useState(false);

  const query = useQuery({
    queryKey: ['share', token],
    queryFn: () => fetchShare(token),
    enabled: token.length > 0,
    retry: false,
    staleTime: 15_000,
  });

  if (revoked) {
    return <StateCard title="This share has been revoked." />;
  }

  if (query.isLoading) {
    return <LoadingCard />;
  }

  if (query.error instanceof ApiError) {
    if (query.error.code === 'SHARE_EXPIRED') {
      return <StateCard title="This share has expired." body="The content is no longer available." />;
    }
    if (query.error.code === 'SHARE_REVOKED') {
      return <StateCard title="This share has been revoked." />;
    }
    if (query.error.code === 'SHARE_NOT_FOUND') {
      return <StateCard title="This share is no longer available." />;
    }
    if (query.error.code === 'PASSWORD_REQUIRED') {
      return <Navigate to={`/s/${token}/password`} replace />;
    }
  }

  if (query.error) {
    const message = query.error instanceof ApiError ? query.error.message : 'Could not load this share.';
    return <StateCard title="Something went wrong." body={message} />;
  }

  if (query.data?.passwordRequired) {
    return <Navigate to={`/s/${token}/password`} replace />;
  }

  const share = query.data;
  if (!share) {
    return <StateCard title="This share is no longer available." />;
  }

  async function onDownload() {
    setDownloading(true);
    try {
      await downloadShare(token, share?.filename ?? 'download');
    } catch (err) {
      if (err instanceof ApiError && err.code === 'PASSWORD_REQUIRED') {
        window.location.assign(`/s/${token}/password`);
        return;
      }
      toast(err instanceof ApiError ? err.message : 'Download failed.', 'err');
    } finally {
      setDownloading(false);
    }
  }

  async function onRevoke() {
    if (!created) {
      return;
    }
    try {
      await revokeShare(token, created.managementToken);
      clearCreated(token);
      clearAccess(token);
      setRevoked(true);
    } catch (err) {
      toast(err instanceof ApiError ? err.message : 'Could not revoke the share.', 'err');
    }
  }

  return (
    <section className="rounded-2xl border border-zinc-200 bg-white p-6 shadow-sm dark:border-zinc-800 dark:bg-zinc-900">
      {share.type === 'TEXT' ? (
        <>
          <div className="mb-4 flex items-center justify-between gap-3">
            <h1 className="text-lg font-semibold tracking-tight">Shared text</h1>
            <CopyButton value={share.content ?? ''} />
          </div>
          <pre className="max-h-[28rem] overflow-auto whitespace-pre-wrap break-words rounded-xl bg-zinc-50 p-4 font-mono text-sm dark:bg-zinc-950">
            {share.content}
          </pre>
        </>
      ) : (
        <>
          <h1 className="truncate text-lg font-semibold tracking-tight">{share.filename ?? 'Shared file'}</h1>
          <p className="mt-2 text-sm text-zinc-500">
            {fileKind(share.contentType, share.filename)}
            {typeof share.fileSize === 'number' ? ` · ${formatBytes(share.fileSize)}` : ''}
          </p>
          <button
            type="button"
            onClick={onDownload}
            disabled={downloading}
            className="mt-6 inline-flex h-11 w-full items-center justify-center rounded-lg bg-zinc-900 text-sm font-medium text-white disabled:opacity-50 dark:bg-zinc-100 dark:text-zinc-950"
          >
            {downloading ? 'Downloading…' : 'Download'}
          </button>
        </>
      )}

      <div className="mt-6 flex items-center justify-between gap-3">
        <Countdown expiresAt={share.expiresAt} />
        {created ? (
          confirming ? (
            <button type="button" onClick={onRevoke} className="text-sm text-red-600">
              Confirm revoke
            </button>
          ) : (
            <button type="button" onClick={() => setConfirming(true)} className="text-sm text-zinc-400 hover:text-zinc-600">
              Revoke
            </button>
          )
        ) : null}
      </div>
    </section>
  );
}

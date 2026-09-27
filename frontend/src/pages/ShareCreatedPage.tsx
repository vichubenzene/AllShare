import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { absoluteShareUrl, revokeShare } from '../api/client';
import { ApiError } from '../api/client';
import { Button } from '../components/Button';
import { CopyButton } from '../components/CopyButton';
import { Countdown } from '../components/Countdown';
import { StateCard } from '../components/StateCard';
import { useToast } from '../components/Toast';
import { formatTimestamp } from '../utils/format';
import { clearAccess, clearCreated, loadCreated } from '../utils/session';

export function ShareCreatedPage() {
  const { token = '' } = useParams();
  const created = loadCreated(token);
  const toast = useToast();
  const [revoked, setRevoked] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [working, setWorking] = useState(false);
  const link = absoluteShareUrl(token);

  async function onRevoke() {
    if (!created) {
      return;
    }
    setWorking(true);
    try {
      await revokeShare(token, created.managementToken);
      clearCreated(token);
      clearAccess(token);
      setRevoked(true);
      toast('Share revoked');
    } catch (err) {
      toast(err instanceof ApiError ? err.message : 'Could not revoke the share.', 'err');
    } finally {
      setWorking(false);
      setConfirming(false);
    }
  }

  if (revoked) {
    return <StateCard title="This share has been revoked." body="The content is no longer available." />;
  }

  return (
    <section className="rounded-2xl border border-zinc-200 bg-white p-6 shadow-sm dark:border-zinc-800 dark:bg-zinc-900">
      <h1 className="text-xl font-semibold tracking-tight">Share created successfully</h1>
      <p className="mt-2 text-sm text-zinc-500 dark:text-zinc-400">
        Send this link. Revoke stays in this browser session only.
      </p>

      <div className="mt-6 rounded-xl border border-zinc-200 bg-zinc-50 p-3 dark:border-zinc-800 dark:bg-zinc-950">
        <p className="break-all font-mono text-sm">{link}</p>
        <div className="mt-3">
          <CopyButton value={link} label="Copy link" />
        </div>
      </div>

      <dl className="mt-6 space-y-3 text-sm">
        <div className="flex items-center justify-between gap-4">
          <dt className="text-zinc-500">Expires</dt>
          <dd className="text-right">
            {created ? (
              <>
                <Countdown expiresAt={created.expiresAt} />
                <span className="mt-1 block text-xs text-zinc-400">{formatTimestamp(created.expiresAt)}</span>
              </>
            ) : (
              'Open the link to check'
            )}
          </dd>
        </div>
        <div className="flex items-center justify-between gap-4">
          <dt className="text-zinc-500">Password</dt>
          <dd>{created?.passwordProtected ? 'Protected' : created ? 'Not protected' : 'Unknown in this session'}</dd>
        </div>
      </dl>

      <div className="mt-6 flex flex-col gap-3">
        <Link
          to={`/s/${token}`}
          className="inline-flex h-11 items-center justify-center rounded-lg border border-zinc-200 text-sm font-medium dark:border-zinc-700"
        >
          Open share
        </Link>
        {created ? (
          confirming ? (
            <div className="grid grid-cols-2 gap-2">
              <Button type="button" onClick={onRevoke} disabled={working} className="bg-red-600 hover:bg-red-500 dark:bg-red-500 dark:text-white">
                {working ? 'Revoking…' : 'Confirm revoke'}
              </Button>
              <button
                type="button"
                onClick={() => setConfirming(false)}
                className="h-11 rounded-lg border border-zinc-200 text-sm dark:border-zinc-700"
              >
                Cancel
              </button>
            </div>
          ) : (
            <button
              type="button"
              onClick={() => setConfirming(true)}
              className="h-11 rounded-lg text-sm text-zinc-500 hover:text-zinc-800 dark:hover:text-zinc-200"
            >
              Revoke share
            </button>
          )
        ) : (
          <p className="text-center text-xs text-zinc-400">
            The revoke control is only available in the browser session that created this share.
          </p>
        )}
      </div>
    </section>
  );
}

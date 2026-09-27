import { useState } from 'react';
import { Navigate, useNavigate, useParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError, fetchShare, verifyPassword } from '../api/client';
import { Button } from '../components/Button';
import { Countdown } from '../components/Countdown';
import { LoadingCard, StateCard } from '../components/StateCard';
import { saveAccess } from '../utils/session';

export function PasswordPage() {
  const { token = '' } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const query = useQuery({
    queryKey: ['share', token],
    queryFn: () => fetchShare(token),
    enabled: token.length > 0,
    retry: false,
    staleTime: 15_000,
  });

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
  }

  if (query.error) {
    const message = query.error instanceof ApiError ? query.error.message : 'Could not load this share.';
    return <StateCard title="Something went wrong." body={message} />;
  }

  if (query.data && !query.data.passwordRequired) {
    return <Navigate to={`/s/${token}`} replace />;
  }

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const result = await verifyPassword(token, password);
      saveAccess(token, result.accessToken);
      queryClient.setQueryData(['share', token], result.share);
      navigate(`/s/${token}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not unlock this share.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="rounded-2xl border border-zinc-200 bg-white p-6 shadow-sm dark:border-zinc-800 dark:bg-zinc-900">
      <h1 className="text-xl font-semibold tracking-tight">Protected share</h1>
      <p className="mt-1 text-sm text-zinc-500">Enter the password to view it.</p>
      <form onSubmit={onSubmit} className="mt-6 space-y-4">
        <label className="block text-sm">
          <span className="mb-1.5 block text-zinc-500">Password</span>
          <input
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="current-password"
            autoFocus
            className="h-11 w-full rounded-lg border border-zinc-200 bg-white px-3 outline-none ring-zinc-400 focus:ring-2 dark:border-zinc-700 dark:bg-zinc-950"
          />
        </label>
        {error ? <p className="text-sm text-red-600 dark:text-red-400">{error}</p> : null}
        <Button type="submit" disabled={submitting || password.length === 0} className="w-full">
          {submitting ? 'Unlocking…' : 'Unlock'}
        </Button>
      </form>
      <div className="mt-6">
        <Countdown expiresAt={query.data?.expiresAt} />
      </div>
    </section>
  );
}

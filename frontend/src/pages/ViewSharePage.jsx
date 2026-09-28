import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ApiError, downloadShare, fetchShare, verifyPassword } from '../api/client.js';
import { CopyButton } from '../components/CopyButton.jsx';
import { Countdown } from '../components/Countdown.jsx';
import { LoadingCard, StateCard } from '../components/StateCard.jsx';
import { useToast } from '../components/Toast.jsx';
import { errorMessage, errorPage } from '../utils/errors.js';
import { fileKind, formatBytes } from '../utils/format.js';
import { clearAccess, nameFromSlug, saveAccess } from '../utils/session.js';

export function ViewSharePage() {
  const { slug = '' } = useParams();
  const navigate = useNavigate();
  const [state, setState] = useState({ status: 'loading' });

  const load = useCallback(async () => {
    setState({ status: 'loading' });
    try {
      const share = await fetchShare(slug);
      if (share.shareUrl && share.shareUrl !== `/${slug}`) {
        navigate(share.shareUrl, { replace: true });
        return;
      }
      if (share.passwordRequired) {
        clearAccess(nameFromSlug(slug));
        setState({ status: 'locked', share });
      } else {
        setState({ status: 'ready', share });
      }
    } catch (error) {
      setState({ status: 'error', error });
    }
  }, [slug, navigate]);

  useEffect(() => {
    load();
  }, [load]);

  if (state.status === 'loading') {
    return <LoadingCard />;
  }
  if (state.status === 'error') {
    const page = errorPage(state.error);
    return <StateCard title={page.title} body={page.body} />;
  }
  if (state.status === 'locked') {
    return (
      <PasswordForm
        slug={slug}
        expiresAt={state.share.expiresAt}
        onUnlocked={(share) => setState({ status: 'ready', share })}
        onGone={(error) => setState({ status: 'error', error })}
      />
    );
  }
  return state.share.type === 'TEXT' ? (
    <TextShare share={state.share} />
  ) : (
    <FileShare slug={slug} share={state.share} onGone={(error) => setState({ status: 'error', error })} />
  );
}

function TextShare({ share }) {
  return (
    <section className="card">
      <div className="row">
        <h1 className="mono">{share.name}</h1>
        <CopyButton value={share.content ?? ''} />
      </div>
      <pre className="content">{share.content}</pre>
      <Countdown expiresAt={share.expiresAt} />
    </section>
  );
}

function FileShare({ slug, share, onGone }) {
  const toast = useToast();
  const [downloading, setDownloading] = useState(false);
  const publicName = share.shareUrl.slice(1);

  async function onDownload() {
    setDownloading(true);
    try {
      await downloadShare(slug, share.filename ?? publicName);
    } catch (err) {
      if (err instanceof ApiError && ['SHARE_EXPIRED', 'SHARE_REVOKED', 'SHARE_NOT_FOUND'].includes(err.code)) {
        onGone(err);
        return;
      }
      toast(errorMessage(err), 'err');
    } finally {
      setDownloading(false);
    }
  }

  return (
    <section className="card">
      <h1 className="mono break">{publicName}</h1>
      <p className="file-meta">
        <strong>{fileKind(share.extension, share.contentType)}</strong>
        <span>{formatBytes(share.fileSize ?? 0)}</span>
      </p>
      {share.filename && share.filename !== publicName ? <p className="muted small">Original name: {share.filename}</p> : null}
      <button type="button" className="btn btn-primary" onClick={onDownload} disabled={downloading}>
        {downloading ? 'Downloading…' : 'Download'}
      </button>
      <Countdown expiresAt={share.expiresAt} />
    </section>
  );
}

function PasswordForm({ slug, expiresAt, onUnlocked, onGone }) {
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  async function onSubmit(event) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const result = await verifyPassword(slug, password);
      saveAccess(nameFromSlug(slug), result.accessToken);
      onUnlocked(result.share);
    } catch (err) {
      if (err instanceof ApiError && ['SHARE_EXPIRED', 'SHARE_REVOKED', 'SHARE_NOT_FOUND'].includes(err.code)) {
        onGone(err);
        return;
      }
      setError(err instanceof ApiError && err.code === 'INVALID_PASSWORD' ? 'Incorrect password.' : errorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="card">
      <h1>🔒 Password Required</h1>
      <p className="muted">This share is password protected.</p>
      <form onSubmit={onSubmit} className="form">
        <label className="field">
          <span>Password</span>
          <input
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="current-password"
            autoFocus
          />
        </label>
        {error ? <p className="error">{error}</p> : null}
        <button type="submit" className="btn btn-primary" disabled={submitting || password.length === 0}>
          {submitting ? 'Unlocking…' : 'Unlock'}
        </button>
      </form>
      <Countdown expiresAt={expiresAt} />
    </section>
  );
}

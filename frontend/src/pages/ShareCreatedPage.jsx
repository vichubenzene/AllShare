import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { absoluteUrl, revokeShare } from '../api/client.js';
import { CopyButton } from '../components/CopyButton.jsx';
import { Countdown } from '../components/Countdown.jsx';
import { StateCard } from '../components/StateCard.jsx';
import { useToast } from '../components/Toast.jsx';
import { errorMessage } from '../utils/errors.js';
import { formatTimestamp } from '../utils/format.js';
import { clearAccess, clearCreated, loadCreated } from '../utils/session.js';

export function ShareCreatedPage() {
  const { slug = '' } = useParams();
  const toast = useToast();
  const [created] = useState(() => loadCreated(slug));
  const [revoked, setRevoked] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [working, setWorking] = useState(false);

  if (revoked) {
    return <StateCard title="This share has been revoked." body="The content is no longer available." />;
  }

  if (!created) {
    return (
      <StateCard
        title="Share details are not available"
        body="The link and management token are only shown in the browser tab that created the share."
      />
    );
  }

  const link = absoluteUrl(created.shareUrl);

  async function onRevoke() {
    setWorking(true);
    try {
      await revokeShare(created.name, created.managementToken);
      clearCreated(created.name);
      clearAccess(created.name);
      setRevoked(true);
      toast('Share revoked');
    } catch (err) {
      toast(errorMessage(err), 'err');
    } finally {
      setWorking(false);
      setConfirming(false);
    }
  }

  return (
    <section className="card">
      <h1>Share Created</h1>

      <p className="label">Your share</p>
      <div className="box">
        <a href={created.shareUrl} className="mono break">
          {link}
        </a>
      </div>
      <CopyButton value={link} label="Copy Link" className="btn btn-primary" />

      <dl className="details">
        <div>
          <dt>Expires</dt>
          <dd>
            <Countdown expiresAt={created.expiresAt} prefix="in " />
            <span className="muted small">{formatTimestamp(created.expiresAt)}</span>
          </dd>
        </div>
        <div>
          <dt>Password</dt>
          <dd>{created.passwordProtected ? 'Required' : 'Not required'}</dd>
        </div>
      </dl>

      <p className="label">Management token</p>
      <p className="muted small">
        Anyone with this token can revoke the share. It is shown only here, so save it now if you may need to revoke
        later.
      </p>
      <div className="box mono break">{created.managementToken}</div>
      <CopyButton value={created.managementToken} label="Copy Management Token" />

      <div className="actions">
        <Link to={created.shareUrl} className="btn btn-secondary">
          Open share
        </Link>
        {confirming ? (
          <>
            <button type="button" className="btn btn-danger" onClick={onRevoke} disabled={working}>
              {working ? 'Revoking…' : 'Confirm revoke'}
            </button>
            <button type="button" className="btn btn-secondary" onClick={() => setConfirming(false)}>
              Cancel
            </button>
          </>
        ) : (
          <button type="button" className="btn btn-secondary" onClick={() => setConfirming(true)}>
            Revoke share
          </button>
        )}
      </div>
    </section>
  );
}

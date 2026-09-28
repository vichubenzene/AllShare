import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError, createFileShare, createTextShare } from '../api/client.js';
import { FilePicker } from '../components/FilePicker.jsx';
import { useToast } from '../components/Toast.jsx';
import { EXPIRATIONS, MAX_FILE_BYTES, NAME_PATTERN } from '../constants.js';
import { errorMessage } from '../utils/errors.js';
import { saveCreated } from '../utils/session.js';

const DRAFT_KEY = 'allshare:draft';

function loadDraft() {
  try {
    return JSON.parse(sessionStorage.getItem(DRAFT_KEY) ?? 'null') ?? {};
  } catch {
    return {};
  }
}

export function CreateSharePage() {
  const navigate = useNavigate();
  const toast = useToast();
  const [draft] = useState(loadDraft);
  const [mode, setMode] = useState(draft.mode ?? 'text');
  const [name, setName] = useState(draft.name ?? '');
  const [content, setContent] = useState(draft.content ?? '');
  const [file, setFile] = useState(null);
  const [expirationMinutes, setExpirationMinutes] = useState(draft.expirationMinutes ?? 60);
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [progress, setProgress] = useState(0);
  const [error, setError] = useState(null);

  // Keeps the typed text if the user leaves to look at an existing share with the same name.
  useEffect(() => {
    sessionStorage.setItem(DRAFT_KEY, JSON.stringify({ mode, name, content, expirationMinutes }));
  }, [mode, name, content, expirationMinutes]);

  const nameValid = NAME_PATTERN.test(name);
  const hasContent = mode === 'text' ? content.trim().length > 0 : file !== null;
  const canSubmit = nameValid && hasContent && !submitting;

  function chooseFile(next) {
    if (next && next.size > MAX_FILE_BYTES) {
      setError('File too large. The limit is 50 MB.');
      return;
    }
    setError(null);
    setFile(next);
  }

  async function onSubmit(event) {
    event.preventDefault();
    if (!canSubmit) {
      return;
    }
    setSubmitting(true);
    setError(null);
    setProgress(0);
    try {
      const created =
        mode === 'text'
          ? await createTextShare({ name, content, expirationMinutes, password })
          : await createFileShare({ name, file, expirationMinutes, password }, setProgress);
      saveCreated(created);
      sessionStorage.removeItem(DRAFT_KEY);
      navigate(`/created/${created.name}`);
    } catch (err) {
      if (err instanceof ApiError && err.code === 'SHARE_NAME_TAKEN') {
        toast(`"${name}" already exists. Showing it. Go back to choose another name.`, 'err');
        navigate(err.shareUrl ?? `/${name}`);
        return;
      }
      setError(errorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  const previewPath = nameValid ? `/${name}${mode === 'file' && file ? extensionSuffix(file.name) : ''}` : null;

  return (
    <section className="card">
      <h1>Temporary Share</h1>
      <p className="muted">Share text or a file under a name you choose. It disappears when it expires.</p>

      <div className="tabs" role="tablist">
        {['text', 'file'].map((item) => (
          <button
            key={item}
            type="button"
            role="tab"
            aria-selected={mode === item}
            className={`tab ${mode === item ? 'tab-active' : ''}`}
            onClick={() => {
              setMode(item);
              setError(null);
            }}
          >
            {item === 'text' ? 'Text' : 'File'}
          </button>
        ))}
      </div>

      <form onSubmit={onSubmit} className="form">
        <label className="field">
          <span>Share name</span>
          <input
            value={name}
            onChange={(event) => setName(event.target.value.trim().toLowerCase())}
            placeholder="vivi"
            maxLength={63}
            autoComplete="off"
            spellCheck={false}
          />
          {name && !nameValid ? (
            <small className="error">Use lowercase letters, numbers, - and _ (start with a letter or number).</small>
          ) : (
            <small className="muted">
              {previewPath ? `${window.location.host}${previewPath}` : 'Lowercase letters, numbers, - and _'}
            </small>
          )}
        </label>

        {mode === 'text' ? (
          <label className="field">
            <span>Text</span>
            <textarea
              value={content}
              onChange={(event) => setContent(event.target.value)}
              placeholder="Paste text here..."
              rows={10}
            />
          </label>
        ) : (
          <div className="field">
            <span>File</span>
            <FilePicker file={file} onFile={chooseFile} />
          </div>
        )}

        {mode === 'file' && submitting ? (
          <div className="progress" aria-label="Upload progress">
            <div style={{ width: `${progress}%` }} />
          </div>
        ) : null}

        <label className="field">
          <span>Expiration</span>
          <select value={expirationMinutes} onChange={(event) => setExpirationMinutes(Number(event.target.value))}>
            {EXPIRATIONS.map((option) => (
              <option key={option.minutes} value={option.minutes}>
                {option.label}
              </option>
            ))}
          </select>
        </label>

        <label className="field">
          <span>Password</span>
          <input
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            placeholder="Optional"
            autoComplete="new-password"
            maxLength={200}
          />
        </label>

        {error ? <p className="error">{error}</p> : null}

        <button type="submit" className="btn btn-primary" disabled={!canSubmit}>
          {submitting ? (mode === 'file' ? `Uploading ${progress}%` : 'Creating…') : 'Create Share'}
        </button>
      </form>
    </section>
  );
}

function extensionSuffix(filename) {
  const match = /\.([A-Za-z0-9]{1,10})$/.exec(filename);
  return match && filename.lastIndexOf('.') > 0 ? `.${match[1].toLowerCase()}` : '';
}

import { Link } from 'react-router-dom';

export function StateCard({ title, body }) {
  return (
    <section className="card state">
      <h1>{title}</h1>
      {body ? <p className="muted">{body}</p> : null}
      <Link to="/" className="btn btn-secondary">
        Create a new share
      </Link>
    </section>
  );
}

export function LoadingCard() {
  return (
    <section className="card state">
      <p className="muted">Loading…</p>
    </section>
  );
}

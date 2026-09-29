import { Link, Outlet } from 'react-router-dom';

export function AppLayout() {
  return (
    <div className="page">
      <header className="header">
        <Link to="/" className="brand">
          All Share
        </Link>
      </header>
      <main>
        <Outlet />
      </main>
      <footer className="footer muted small">Shares expire on their own. No account required.</footer>
    </div>
  );
}
